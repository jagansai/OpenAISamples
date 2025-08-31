# 03-rag-loader

This module provides a small RAG (retrieval-augmented generation) demo using Spring AI.
It exposes a chat UI and an endpoint to upload documents (or logs) which are indexed into a VectorStore.

Quick start

- Build and run the module from its directory:

```powershell
cd 03-rag-loader
./start.ps1
```

- Open the chat UI in your browser (default port shown when the app starts).
- Use the Upload Log button to POST a log file to `/api/upload-log` which indexes it into the vector store.
- Ask questions that reference content in the uploaded log — the RAG advisor will retrieve relevant chunks.

Notes and tips

- The upload handler preserves raw log text by wrapping it in a Markdown code fence before creating Documents. This avoids markdown parsing transforming lines.
- If you need deterministic chunking, tune `TokenTextSplitter` settings in `AIConfig.java`.
- For debugging, check the application logs for `Calling EmbeddingModel for document id = ...` entries which indicate indexing progress.


