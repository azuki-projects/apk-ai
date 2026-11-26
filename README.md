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
3. Build and install the APK.
4. Open the app and enter your Groq API key when prompted.
5. Use the **API key** button in the header to change or clear the saved key.
6. Connect to the internet and send a message.

## API
- POST https://api.groq.com/openai/v1/chat/completions
- Authorization: Bearer YOUR_GROQ_API_KEY
- Model: openai/gpt-oss-120b

## Key safety
The API key is entered inside the app and saved in app-private SharedPreferences on that device. It is not committed to this repository. This is convenient for personal testing, but it is not secure storage against a compromised/rooted device, and a key used by a distributed app can still be extracted or abused. For a public release, route requests through a backend and keep the key in a server-side environment variable.
