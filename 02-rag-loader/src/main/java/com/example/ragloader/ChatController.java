package com.example.ragloader;

import java.util.logging.Logger;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

@RestController
@RequestMapping("/")
public class ChatController {

    private Logger logger = Logger.getLogger(ChatController.class.getName());

    private ChatClient chatClient;
    private EmbeddingModel embeddingModel;
    private VectorStore vectorStore;

    public ChatController(ChatMemory chatMemory, ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel,
            VectorStore vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;

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

        // Convert markdown to HTML
        var document = markdownParser.parse(markdown == null ? "" : markdown);
        String html = htmlRenderer.render(document);

        return ResponseEntity.ok(new ChatResponse(markdown, html));
    }


}
