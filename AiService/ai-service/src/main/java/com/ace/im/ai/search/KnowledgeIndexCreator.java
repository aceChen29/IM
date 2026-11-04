package com.ace.im.ai.search;

import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.*;
import io.milvus.v2.service.collection.request.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("index-admin")
public class KnowledgeIndexCreator implements CommandLineRunner {
    private final MilvusClientV2 client;
    private final String collection;

    public KnowledgeIndexCreator(
            MilvusClientV2 client,
            @Value("${ai.milvus.collection:im_knowledge_v1}") String collection) {
        this.client = client;
        this.collection = collection;
    }

    @Override
    public void run(String... args) {
        // 先查询集合是否存在；存在就跳过本次初始化。
        if (client.hasCollection(
                HasCollectionReq.builder() // 创建“检查集合是否存在”的请求构建器。
                        .collectionName(collection) // 目标集合名，对应ai.milvus.collection。
                        .build())) // 构建请求对象；hasCollection才会向Milvus发请求。
            return;
        var schema = client.createSchema();
        schema.addField(
                // 创建字段定义构建器；schema.addField将字段定义加入集合结构。
                AddFieldReq.builder()
                        // 字段名：每个文本切片的唯一标识；不是整份文档的ID。
                        .fieldName("chunk_id")
                        // 字段类型：可变长度字符串。
                        .dataType(DataType.VarChar)
                        // 设为集合主键，用于标识实体；主键不等于自动生成业务ID。
                        .isPrimaryKey(true)
                        // 关闭主键自动生成；入库时由应用传入chunk_id。
                        .autoID(false)
                        // 字符串最大长度为128字节（UTF-8），不是128个汉字。
                        .maxLength(128)
                        // 构建字段定义对象；build只生成对象，不向服务器发送请求。
                        .build());

        schema.addField(
                AddFieldReq.builder()
                        // 所属文档ID；同一文档的多个切片共享doc_id。
                        .fieldName("doc_id")
                        .dataType(DataType.VarChar)
                        // 该字符串字段的最大UTF-8字节数；根据字段用途分别设定。
                        .maxLength(64)
                        .build());
        schema.addField(
                AddFieldReq.builder()
                        // 文档标题，用于展示来源。
                        .fieldName("title")
                        .dataType(DataType.VarChar)
                        // 该字符串字段的最大UTF-8字节数；根据字段用途分别设定。
                        .maxLength(1024)
                        .build());
        schema.addField(
                AddFieldReq.builder()
                        // 切片原文；用于BM25分词，也作为回答证据返回。
                        .fieldName("text")
                        .dataType(DataType.VarChar)
                        // 该字符串字段的最大UTF-8字节数；根据字段用途分别设定。
                        .maxLength(8192)
                        // 开启该字符串字段的文本分析，供BM25处理原文。
                        .enableAnalyzer(true)
                        // 选择中文分析器；分词规则会影响关键词召回。
                        .analyzerParams(Map.of("type", "chinese"))
                        .build());
        schema.addField(
                AddFieldReq.builder()
                        // 稠密向量字段，保存Embedding模型输出的语义向量。
                        .fieldName("dense")
                        // 稠密浮点向量类型，每个维度是浮点数。
                        .dataType(DataType.FloatVector)
                        // 向量固定为1024维，必须与Embedding模型输出维度一致。
                        .dimension(1024)
                        .build());
        schema.addField(
                AddFieldReq.builder()
                        // 稀疏向量字段，由下面的BM25函数生成。
                        .fieldName("sparse")
                        // 稀疏浮点向量类型；不在此处指定固定dimension。
                        .dataType(DataType.SparseFloatVector)
                        .build());
        schema.addFunction(
                // 定义Milvus内置函数，建立text到sparse的转换关系。
                CreateCollectionReq.Function.builder()
                        // 函数名称，在本集合内标识这个函数。
                        .name("text_bm25")
                        // 使用BM25函数，根据文本生成供关键词检索的稀疏向量。
                        .functionType(FunctionType.BM25)
                        // 函数输入：text字段中的切片原文。
                        .inputFieldNames(List.of("text"))
                        // 函数输出：自动写入sparse字段，应用插入时不用手填。
                        .outputFieldNames(List.of("sparse"))
                        .build());
        var dense =
                // 创建向量索引配置；索引用于加速对应字段的检索。
                IndexParam.builder()
                        .fieldName("dense")
                        // 由Milvus选择自动索引策略。
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        // 稠密向量使用余弦相似度；比较文本语义的接近程度。
                        .metricType(IndexParam.MetricType.COSINE)
                        .build();
        var sparse =
                IndexParam.builder()
                        .fieldName("sparse")
                        // 为稀疏向量建立倒排索引，支持BM25关键词召回。
                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                        // 稀疏检索使用BM25评分，与上面的BM25函数对应。
                        .metricType(IndexParam.MetricType.BM25)
                        .build();
        client.createCollection(
                // 构建创建集合请求；前面定义schema和索引本身尚未创建集合。
                CreateCollectionReq.builder()
                        // 指定要创建的集合名称。
                        .collectionName(collection)
                        // 设置集合字段及BM25函数定义。
                        .collectionSchema(schema)
                        // 同时配置稠密向量索引和稀疏向量索引。
                        .indexParams(List.of(dense, sparse))
                        .build());
        // 将集合加载到可检索状态；这是服务端操作，不是读取原始文件。
        client.loadCollection(
                LoadCollectionReq.builder()
                        .collectionName(collection) // 要加载的集合名。
                        .build());
    }
}