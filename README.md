# OpenAISamples

## Table of Contents

- [01-chat-openai](01-chat-openai/)

## 01-chat-openai

A Spring Boot sample demonstrating the Spring AI chat client.

Example request / response (use JSON):

Request:

```json
{
  "prompt": "Summarize the benefits of Spring AI in one sentence."
}
```

Response:

```json
{
  "content": "Spring AI enhances application development by providing seamless integration of artificial intelligence capabilities, enabling faster innovation, improved decision-making, and more intelligent automation within Spring-based projects."
}
```

Notes:

- Start the Spring Boot app (default port 8080) and send a POST to `/api/chat` with the JSON request above.
- See `chat.http` in the project root for example VS Code REST Client requests.
 
