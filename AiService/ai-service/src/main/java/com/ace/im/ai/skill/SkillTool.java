package com.ace.im.ai.skill;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按原教程实现两层技能加载：catalog是常驻系统提示词的名称/描述清单，
 * loadSkill是模型按需读取的SKILL.md正文。启动时读入classpath资源，支持IDE和JAR。
 * 这里只提供规程，实际检索和入库仍交给RagTool。
 */
@Component
@Slf4j
public class SkillTool {
    /** 技能名 -> 完整正文，启动时加载，运行时只读。 */
    private final Map<String, String> bodies = new LinkedHashMap<>();
    private String catalog = "";

    @PostConstruct
    public void load() {
        StringBuilder sb = new StringBuilder("## 可用技能（Skills）\n")
                .append("下列技能封装了本系统特有的操作规程。当用户的意图命中某个技能时，")
                .append("必须先调用 loadSkill 拿到它的完整说明，再严格按说明执行，不要凭常识直接动手。\n\n");
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver(getClass().getClassLoader())
                    .getResources("classpath*:skills/*/SKILL.md");
            for (Resource resource : resources) {
                String[] parts = resource.getContentAsString(StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .split("(?m)^---\\s*$", 3);
                String name = parts.length < 3 ? "" : value(parts[1], "name");
                String description = parts.length < 3 ? "" : value(parts[1], "description");
                if (name.isBlank() || description.isBlank()) {
                    log.error("SKILL.md 缺少 frontmatter 或 name/description，已跳过: {}", resource.getURL());
                    continue;
                }
                String body = parts[2].strip();
                bodies.put(name, body);
                sb.append("- `").append(name).append("`：").append(description).append('\n');
                log.info("Skill 已注册: {}（正文 {} 字符）", name, body.length());
            }
        } catch (Exception e) {
            log.error("Skill 扫描失败，Agent 将以无技能模式运行", e);
        }
        if (!bodies.isEmpty()) {
            catalog = sb.toString();
        }
        log.info("Skill 加载完成，共 {} 个: {}，清单 {} 字符", bodies.size(), bodies.keySet(), catalog.length());
    }

    /** 第一层：由ChatService追加到系统提示词。 */
    public String catalog() {
        return catalog;
    }

    @Tool("加载一个技能的完整操作说明。当用户的请求命中系统提示词【可用技能】清单里的某个技能时，"
            + "先调用本工具拿到该技能的详细步骤，再严格按步骤执行。")
    public String loadSkill(@P("技能名，必须是清单里列出的名字，例如 kb-curator") String skillName) {
        String body = bodies.get(skillName);
        if (body == null) {
            log.warn("Skill 不存在: {}", skillName);
            return "没有名为 [" + skillName + "] 的技能。当前可用技能：" + bodies.keySet()
                    + "。请从中选择，或者不使用技能直接回答。";
        }
        log.info("Skill 已加载: {}（{} 字符）", skillName, body.length());
        return "以下是技能 [" + skillName + "] 的完整说明，请严格按其中的步骤执行：\n\n" + body;
    }

    /** 与原教程一致，仅解析单行key: value格式的元数据。 */
    private String value(String frontMatter, String key) {
        for (String line : frontMatter.split("\n")) {
            String t = line.strip();
            if (t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).strip();
            }
        }
        return "";
    }
}
