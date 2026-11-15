# Assignments Chatbot

Ask natural-language questions about learner assignment submissions
("How many submitted?", "Who submitted the most?").

- **Backend**: Spring Boot 4.1.2, Spring AI 2.0.2, Java 27. The model (`gpt-6-luna` on Azure AI Foundry) calls tools
  that read the live Trainer Central entries API, and the answer is streamed over SSE (`POST /api/chat/stream`).
- **Frontend**: React 19 + Vite. It renders the stream token by token, with markdown and tables.

## Setup

1. Put your key in `secrets.env` at the repo root: `AZURE_FOUNDRY_API_KEY=...` (git-ignored; `application.yaml` imports it).
2. Run the backend (JDK 27): `cd backend && mvn spring-boot:run`
3. Run the frontend: `cd frontend && npm install && npm run dev`, then open http://localhost:5173

## Configuration

`backend/src/main/resources/application.yaml`. Override with env vars or `secrets.env`:
`AZURE_FOUNDRY_API_KEY`, `AZURE_FOUNDRY_BASE_URL` (required: your Foundry resource, in the form
`https://<your-resource>.services.ai.azure.com/openai/v1`), `AZURE_FOUNDRY_MODEL` (default `gpt-6-luna`).

## Data notes

The API holds free-text notifications with duplicates, test entries and learner aliases, so the system prompt
(`ChatConfig`) tells the model to de-duplicate by Message ID, merge aliases and exclude tests.
