package com.ace.im.ai.service;

import com.ace.im.ai.model.dto.KnowledgeInput;
import com.google.gson.JsonObject;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.model.embedding.EmbeddingModel;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class KnowledgeService implements ContentRetriever {
    private final MilvusClientV2 client;
    private final EmbeddingModel embeddingModel;
    private final String collection;

    public KnowledgeService(
            MilvusClientV2 client,
            EmbeddingModel embeddingModel,
            @Value("${ai.milvus.collection:im_knowledge_v1}") String collection) {
        this.client = client;
        this.embeddingModel = embeddingModel;
        this.collection = collection;
    }

    public String ingest(KnowledgeInput input) {
        return ingest(Document.from(input.text()), input.title());
    }

    /** 文档加载与动态文本输入共用同一条入库流程。 */
    public String ingest(Document document, String title) {
        String docId = UUID.randomUUID().toString();
        List<JsonObject> rows = new ArrayList<>();
        // 官方递归切分器：优先按段落拆分，过长时逐级按行、句子、单词等更细边界拆分。
        // 两参数重载按字符计量：每片最多600字符，重叠预算最多100字符，不是Token数。
        // 重叠尽量保留完整句子，因此不保证相邻片段恰好重叠100字符。
        List<TextSegment> segments = DocumentSplitters.recursive(600, 100)
                .split(document);
        // 一次提交切片列表；由官方模型实现处理批量限制，不保证仅发一次HTTP请求。
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        if (embeddings.size() != segments.size()) {
            throw new IllegalStateException("Embedding返回数量与切片数量不一致");
        }
        for (int part = 0; part < segments.size(); part++) {
            String text = segments.get(part).text();
            // 按片段顺序取得批量返回的向量。
            float[] vector = embeddings.get(part).vector();
            if (vector.length != 1024) throw new IllegalStateException("Embedding 维度与索引不一致");
            JsonObject row = new JsonObject();
            row.addProperty("chunk_id", docId + ":" + part);
            row.addProperty("doc_id", docId);
            row.addProperty("title", title);
            row.addProperty("text", text);
            row.add("dense", new com.google.gson.Gson().toJsonTree(vector));
            rows.add(row);
        }
        // BM25 的 sparse 由 Milvus 根据 text 自动生成，不手动提供。
        // builder只组装请求；client.insert才把切片原文、元数据和dense向量发送到Milvus。
        client.insert(
                InsertReq.builder()
                        // 写入的目标集合，必须已按上一节创建。
                        .collectionName(collection)
                        // 每个JsonObject是一条切片实体；字段名称、类型需与schema一致。
                        .data(rows)
                        // 构建批量插入请求；sparse由服务端BM25函数生成。
                        .build());
        return docId;
    }

    /** 适配官方ContentRetriever，由RetrievalAugmentor调用。 */
    @Override
    public List<Content> retrieve(Query query) {
        String text = query.text();
        if (text == null || text.isBlank() || text.length() > 8000)
            throw new IllegalArgumentException("检索问题不能为空且不能超过8000字符");
        float[] vector = embeddingModel.embed(text).content().vector();
        AnnSearchReq dense = AnnSearchReq.builder()
                .vectorFieldName("dense") // 语义向量字段。
                .vectors(List.of(new FloatVec(vector))) // 本次只有一个查询向量。
                .topK(20) // 本路最多召回20条候选。
                .params("{}") // 不覆盖索引搜索参数。
                .build();
        AnnSearchReq sparse = AnnSearchReq.builder()
                .vectorFieldName("sparse") // BM25稀疏字段。
                .vectors(List.of(new EmbeddedText(text))) // 服务端处理原始问题文本。
                .topK(20)
                .params("{}")
                .build();
        var response = client.hybridSearch(HybridSearchReq.builder()
                .collectionName(collection) // 所有用户检索同一个共享集合。
                .searchRequests(List.of(dense, sparse)) // 交给Milvus融合两路候选。
                .ranker(new RRFRanker(60)) // 60是RRF平滑参数，不是条数。
                .topK(20) // RRF融合后保留最多20条，交给qwen3-rerank精排后选5条。
                .outFields(List.of("chunk_id", "title", "text")) // 返回正文与引用信息。
                .consistencyLevel(ConsistencyLevel.STRONG) // 读取已确认写入的数据。
                .build());
        if (response.getSearchResults().isEmpty()) return List.of();
        // 外层按问题分组，get(0)取本次唯一问题的结果。
        return response.getSearchResults().get(0).stream().map(hit -> {
            var entity = hit.getEntity();
            Metadata metadata = Metadata.from("chunk_id", String.valueOf(entity.get("chunk_id")))
                    .put("title", String.valueOf(entity.get("title")));
            return Content.from(TextSegment.from(String.valueOf(entity.get("text")), metadata));
        }).toList();
    }
}
