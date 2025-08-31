# 02-rag-loader — README

## 1. Goal of this module

This module is a small Spring Boot demo that shows how to: accept user prompts via an HTTP API, ask an LLM (via Spring AI ChatClient) for a Markdown response, convert that Markdown to HTML, and store / search small documents in a local in-memory vector store for retrieval-augmented responses.

The implementation is intentionally simple and targeted for local development and experimentation.

## 2. What APIs are used

- Spring Boot (web starter) — application framework and HTTP endpoints.
- Spring AI ChatClient — sends prompts to the configured LLM and applies advisors.
  - The controller constructs a ChatClient via the provided `ChatClient.Builder` with these default advisors:
    - `MessageChatMemoryAdvisor` (stores conversation memory)
    - `QuestionAnswerAdvisor` (wired to the app's `VectorStore`) 
    - `SimpleLoggerAdvisor`
- Spring AI `EmbeddingModel` — used to compute embeddings for documents and queries.
-- `VectorStore` (Spring AI abstraction) — this project provides a `SimpleVectorStore` bean (in-memory) via `AIConfig`.
- Flexmark (flexmark-all) — converts Markdown produced by the LLM into HTML.
- Jackson (via Spring Boot) — JSON serialization for API requests/responses.

## 3. VectorStore (SimpleVectorStore)

This module uses Spring AI's `SimpleVectorStore` (an in-memory VectorStore) which is created and exposed as a bean in `AIConfig`. That means:

- No separate vector-store source file is required in this module — `SimpleVectorStore` is constructed using the application's `EmbeddingModel`.
- Documents are split and accepted into the `VectorStore` at startup by the `ApplicationRunner` in `AIConfig`.

Notes:

- `SimpleVectorStore` is suitable for local development and small datasets. It keeps embeddings in memory and performs an O(N) similarity scan.
- For larger datasets or production, replace the bean with a persistent VectorStore (e.g., Redis/RediSearch or an ANN service).

## 4. API(s) exposed by this module

- POST `/api/chat` — accepts JSON and returns the LLM response in both Markdown and HTML.

  Request JSON (example):
  ```json
  { "prompt": "Explain recursion in simple terms." }
  ```

  Response JSON (example):
  ```json
  { "markdown": "# Recursion\n...", "html": "<h1>Recursion</h1>..." }
  ```

  Notes:
  - The controller instructs the model to respond using Markdown. The server converts the Markdown to HTML using flexmark and returns both forms.
  - The ChatClient used by the controller includes the vector-store based advisor (`QuestionAnswerAdvisor`) so RAG-style behavior (if documents are loaded) will be applied.

## How to build and run (current implementation)

From the repository root (PowerShell):

```powershell
# Build the module
mvn -DskipTests package -pl 02-rag-loader -am

# Start the module (there is a helper start script in the module)
cd 02-rag-loader
.\start.ps1
```

The web UI is available at `http://localhost:8080` and the simple client at `/chat.html` will POST to `/api/chat`.

## Screenshot

Screenshot (for quick reference):

![UI screenshot](src/main/resources/images/SS.jpg)

(absolute path in repo: `02-rag-loader/src/main/resources/images/SS.jpg`)

---

Notes and caveats
- This README documents the current (in-memory) implementation only. For production use you should replace the in-memory store with a persistent/managed vector database (Redis, Pinecone, Milvus, etc.), add sanitization for HTML rendering in the client, and add batching/optimizations for embedding generation.

If you want, I can:
- Add a Maven profile to switch between in-memory and Redis-backed VectorStore.
- Add unit tests for `InMemoryVectorStore` (happy path + edge cases).
- Add client-side sanitization (DOMPurify) to the `chat.html`.

Tell me which of those you'd like next.
