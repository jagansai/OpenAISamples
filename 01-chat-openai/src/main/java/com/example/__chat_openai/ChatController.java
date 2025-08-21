package com.example.__chat_openai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }

    @PostMapping("/api/chat")
    Output chat(@RequestBody @Valid Input input) {
        String response = chatClient.prompt(input.prompt()).call().content();
        return new Output(response);
    }

    record Input(@NotBlank String prompt) { }
    record Output(String content) { }

}
