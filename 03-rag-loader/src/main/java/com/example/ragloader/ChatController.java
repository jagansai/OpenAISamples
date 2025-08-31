package com.example.ragloader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

@RestController
@RequestMapping("/")
public class ChatController {

    private Logger logger = Logger.getLogger(ChatController.class.getName());

    private ChatClient chatClient;
    private EmbeddingModel embeddingModel;
    private VectorStore vectorStore;
    private AIConfig aiConfig;

    public ChatController(ChatMemory chatMemory, ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel,
            VectorStore vectorStore, AIConfig aiConfig) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.aiConfig = aiConfig;

        this.chatClient = chatClientBuilder.defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                QuestionAnswerAdvisor.builder(vectorStore).build(),
                new SimpleLoggerAdvisor()).build();
    }

    record ChatRequest(String systemMessage, String userMessage, String prompt) { }
    record ChatResponse(String markdown, String html) { }

    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    @PostMapping(path = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        // Build a structured prompt that instructs the model to return markdown
        StringBuilder sb = new StringBuilder();
        sb.append("Respond ONLY in Markdown format. Use headings, lists, and code blocks as appropriate.\n\n");
        if (request.systemMessage() != null && !request.systemMessage().isBlank()) {
            sb.append("System: ").append(request.systemMessage()).append("\n\n");
        }
        if (request.userMessage() != null && !request.userMessage().isBlank()) {
            sb.append("User: ").append(request.userMessage()).append("\n\n");
        }
        if (request.prompt() != null && !request.prompt().isBlank()) {
            sb.append("Prompt: ").append(request.prompt()).append("\n\n");
        }

        String prompt = sb.toString();
        String markdown = chatClient.prompt(prompt).call().content();

        var document = markdownParser.parse(markdown == null ? "" : markdown);
        String html = htmlRenderer.render(document);

        return ResponseEntity.ok(new ChatResponse(markdown, html));
    }

    // New endpoint to upload logs on-the-fly and load into VectorStore
    @PostMapping(path = "/api/upload-log", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadLog(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded");
        }

        try {
            byte[] bytes = file.getBytes();
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            // Wrap raw text in a Markdown code fence so the MarkdownDocumentReader will
            // preserve it verbatim (avoids markdown transformations that can split lines).
            String text = new String(bytes, StandardCharsets.UTF_8);
            text = text.replace('\u0001', '|');
            String fenced = "```text\n" + text + "\n```";

            org.springframework.core.io.ByteArrayResource fencedResource = new org.springframework.core.io.ByteArrayResource(fenced.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MarkdownDocumentReader reader = new MarkdownDocumentReader(fencedResource, MarkdownDocumentReaderConfig.defaultConfig());
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocuments = splitter.apply(reader.get());
            vectorStore.accept(splitDocuments);
            logger.info("Uploaded and loaded file into VectorStore: " + file.getOriginalFilename());
            return ResponseEntity.ok("File uploaded and indexed: " + file.getOriginalFilename());
        } catch (Exception e) {
            logger.severe("Failed to upload file: " + e.getMessage());
            throw e;
        }
    }

}
