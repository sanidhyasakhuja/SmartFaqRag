# SmartFaqRag 🚀

A simple and powerful RAG (Retrieval-Augmented Generation) pipeline built with Spring AI and Spring Boot for intelligent FAQ management and question answering.

## 🎯 What is this?

SmartFaqRag helps you build an intelligent FAQ system that can:
- Store and search through your documents
- Answer questions based on your content
- Use any AI model you want (local or paid)

## ✨ Features

- 🔍 **Smart Document Search** - Find relevant information quickly
- 🤖 **Flexible Model Support** - Use any local or paid AI model
- 🌐 **OpenRouter Integration** - Seamless access to 100+ models through one API
- 📚 **Easy Document Management** - Simple APIs to add and query documents
- 🚀 **Spring Boot** - Fast, reliable, and easy to deploy

## 🛠️ Quick Start

### Prerequisites
- Java 17+
- Maven or Gradle

### Installation

1. **Clone the repo**
```bash
git clone https://github.com/sanidhyasakhuja/SmartFaqRag.git
cd SmartFaqRag
```

2. **Configure your model** (Choose one option)

**Option 1: Using OpenRouter (Recommended)**
```properties
# application.properties
spring.ai.openai.base-url=https://openrouter.ai/api/v1
spring.ai.openai.api-key=${OPENROUTER_API_KEY}
spring.ai.openai.chat.options.model=meta-llama/llama-3.1-8b-instruct:free
```

**Option 2: Using Local Models (LM Studio)**
```properties
spring.ai.openai.base-url=http://localhost:1234/v1
spring.ai.openai.api-key=lm-studio
```

**Option 3: Using Other Paid APIs**
```properties
# OpenAI
spring.ai.openai.api-key=${OPENAI_API_KEY}

# OR Anthropic
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
```

3. **Run the application**
```bash
./mvnw spring-boot:run
```

## 📖 Usage

Currently, the app works with **preloaded mock documents** from the `resources` folder. 

### Ask Questions
```bash
POST /api/query

{
  "question": "What is your return policy?"
}
```

### Response
```json
{
  "answer": "Based on the documents, our return policy is...",
  "sources": ["relevant document excerpts"]
}
```

## 🤖 Model Options

### OpenRouter (Easiest Way)
Use [OpenRouter](https://openrouter.ai/) to access multiple models with one API:
- **Free models**: Llama, Mistral, and more
- **Paid models**: GPT-4, Claude, Gemini, and 100+ others
- **One API key** for everything
- **Pay-as-you-go** pricing

Get your API key at: https://openrouter.ai/

### Local Models
Run models on your own machine:
1. Download [LM Studio](https://lmstudio.ai/)
2. Load any model (Llama, Mistral, etc.)
3. Start the server
4. Point the app to `http://localhost:1234/v1`

### Direct API Access
- **OpenAI**: Use GPT-3.5, GPT-4
- **Anthropic**: Use Claude models
- **Others**: Any OpenAI-compatible API

## 🔮 Coming Soon

- **Document Upload API** - Add your own documents dynamically
- **PostgreSQL + pgvector** - Production-grade vector storage for better performance and scalability
- Conversation history
- Multiple document formats
- Batch processing

## 🤝 Contributing

Pull requests are welcome! Feel free to:
- Report bugs
- Suggest features
- Improve documentation

## 📝 License

MIT License

## 👤 Author

**Sanidhya Sakhuja**
- GitHub: [@sanidhyasakhuja](https://github.com/sanidhyasakhuja)

---

⭐ Star this repo if you find it useful!
