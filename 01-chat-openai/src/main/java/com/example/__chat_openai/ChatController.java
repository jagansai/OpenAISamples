package com.example.__chat_openai;

import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@RestController
@RequestMapping("/")
public class ChatController {

    // Accept an optional system message, an optional user message, and a required prompt
    record Input(String systemMessage, String userMessage, String prompt) { }
    record Output(String content) { }

    // Quiz request: topic and number of questions
    record QuizRequest(String topic, int count) { }

    // Typed response matching the requested JSON shape
    record QuizResponse(java.util.List<QuestionAndAnswer> QuestionsAndAnswers) { }

    record QuestionAndAnswer(String Question, java.util.List<String> Choices, int Answer) { }



    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }

    @PostMapping("/api/chat")
    Output chat(@RequestBody @Valid Input input) {
        // Build a structured prompt for the LLM from the provided system and user messages.
    // systemMessage and userMessage are optional; prompt may be omitted — fall back to userMessage.
        StringBuilder sb = new StringBuilder();
        if (input.systemMessage() != null && !input.systemMessage().isBlank()) {
            sb.append("System: ").append(input.systemMessage()).append("\n\n");
        }
        if (input.userMessage() != null && !input.userMessage().isBlank()) {
            sb.append("User: ").append(input.userMessage()).append("\n\n");
        }
        // If prompt was not provided, use the userMessage as a fallback
        String prompt = (input.prompt() != null && !input.prompt().isBlank()) ? input.prompt() : input.userMessage();
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either 'prompt' or 'userMessage' must be provided and non-blank");
        }
        sb.append("Prompt: ").append(prompt);

        String structuredPrompt = sb.toString();
        String response = chatClient.prompt(structuredPrompt).call().content();
        return new Output(response);
    }

    @PostMapping("/api/quiz")
    QuizResponse quiz(@RequestBody @Valid QuizRequest request) {
        if (request.topic() == null || request.topic().isBlank() || request.count() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'topic' must be provided and 'count' must be > 0");
        }

        // Ask the LLM to produce a strict JSON object with QuestionsAndAnswers array
        String promptTemplate = """
            Generate %d quiz questions on the topic '%s'. Respond with ONLY the JSON object and nothing else.
            The JSON must have the following shape:
            { "QuestionsAndAnswers": [ { "Question": "...", "Choices": ["..."], "Answer": 1 } ] }
            """;

        String prompt = String.format(promptTemplate, request.count(), request.topic());

        String llmOutput = chatClient.prompt(prompt).call().content();

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(llmOutput);
            // convert to typed response
            QuizResponse resp = mapper.treeToValue(node, QuizResponse.class);
            return resp;
        }
        catch (JsonProcessingException e) {
            // Fallback: try to extract the first JSON object/array from the LLM output
            Optional<QuizResponse> fallback = parseJSON1(llmOutput, mapper);
            if (fallback.isPresent()) {
                return fallback.get();
            }

            Optional<QuizResponse> fallback2 = parseJSON2(llmOutput, mapper);
            if (fallback2.isPresent()) {
                return fallback2.get();
            }

            // If we get here, parsing failed
            String trimmed = llmOutput == null ? "" : llmOutput.trim();
            String msg = String.format("LLM returned invalid JSON and extraction attempts failed. parseError=%s; output=%s",
                    e.getMessage(), trimmed.length() > 500 ? trimmed.substring(0, 500) + "..." : trimmed);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, msg);
        }
    }

    private Optional<QuizResponse> parseJSON1(String llmOutput, ObjectMapper mapper) {
        String trimmed = llmOutput == null ? "" : llmOutput.trim();
        // Try to find a top-level JSON object
        int objStart = trimmed.indexOf('{');
        int objEnd = trimmed.lastIndexOf('}');
        if (objStart != -1 && objEnd != -1 && objEnd > objStart) {
            String candidate = trimmed.substring(objStart, objEnd + 1);
            try {
                JsonNode node = mapper.readTree(candidate);
                return Optional.of(mapper.treeToValue(node, QuizResponse.class));
            } catch (JsonProcessingException ignored) {
                // fall through to try array
            }
        }
        return Optional.empty();
    }

    private Optional<QuizResponse> parseJSON2(String llmOutput, ObjectMapper mapper) {
        String trimmed = llmOutput == null ? "" : llmOutput.trim();
        // Try to find a top-level JSON array
        int arrStart = trimmed.indexOf('[');
        int arrEnd = trimmed.lastIndexOf(']');
        if (arrStart != -1 && arrEnd != -1 && arrEnd > arrStart) {
            String candidate = trimmed.substring(arrStart, arrEnd + 1);
            try {
                JsonNode node = mapper.readTree(candidate);
                return Optional.of(mapper.treeToValue(node, QuizResponse.class));
            } catch (JsonProcessingException ignored) {
                // fall through to try object
            }
        }
        return Optional.empty();
    }
}
