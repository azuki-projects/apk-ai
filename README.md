# Groq AI Chat

A native Java Android chat app for CodeAssist, powered by Groq's OpenAI-compatible Chat Completions API.

## Features
- Model: openai/gpt-oss-120b
- Multi-turn conversation context
- New chat button
- User and assistant message bubbles
- Background network requests
- Loading and connection status
- API error messages
- No third-party libraries

## Build in CodeAssist
1. Open this repository or download it as a ZIP.
2. Open the project root in CodeAssist.
3. Edit GROQ_API_KEY in app/src/main/java/com/azuki/groqchat/MainActivity.java.
4. Build and install the APK.
5. Connect to the internet and send a message.

## API
- POST https://api.groq.com/openai/v1/chat/completions
- Authorization: Bearer YOUR_GROQ_API_KEY
- Model: openai/gpt-oss-120b

## Key safety
The API key is intentionally not committed. Never publish a real API key in a public repository or APK: APK contents can be inspected and keys can be extracted. For a distributed app, route requests through a backend and keep the key in a server-side environment variable.
