# Smart FAQ RAG Assistant

A production-ready Retrieval-Augmented Generation (RAG) system built with Spring Boot and Spring AI that enables users to upload documents and ask questions powered by AI. The system chunks documents, generates embeddings, and uses vector similarity search to provide contextually relevant answers.

## 📋 Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [API Endpoints](#api-endpoints)
- [Current Limitations](#current-limitations)
- [Future Improvements](#future-improvements)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## ✨ Features

### Current Implementation

- **Multi-format Document Upload**: Support for TXT, PDF, and Markdown files
- **Batch Processing**: Upload multiple files simultaneously with progress tracking
- **Direct Text Input**: Paste text content directly without file uploads
- **FAQ Management**: Dedicated endpoint for FAQ ingestion
- **Intelligent Chunking**: Configurable text chunking with overlap for context preservation
- **Vector Search**: Similarity-based document retrieval using embeddings
- **RAG Pipeline**: Complete question-answering system with context injection
- **Interactive UI**: Modern, responsive web interface with drag-and-drop support
- **Document Management**: View statistics, list sources, and delete documents
- **Memory Monitoring**: Real-time JVM memory usage tracking
- **Health Checks**: System status monitoring
- **CORS Enabled**: Ready for cross-origin requests

## 🏗️ Architecture

### Current Architecture (In-Memory)

```
┌─────────────┐
│   Client    │
│  (Browser)  │
└──────┬──────┘
       │ HTTP
       ▼
┌─────────────────────────────────┐
│     Spring Boot Application     │
│                                 │
│  ┌──────────────────────────┐  │
│  │  DocumentUploadController│  │
│  └────────────┬─────────────┘  │
│               │                 │
│  ┌────────────▼─────────────┐  │
│  │    DocumentService       │  │
│  │  - PDF Extraction        │  │
│  │  - Text Chunking         │  │
│  │  - Batch Processing      │  │
│  └────────────┬─────────────┘  │
│               │                 │
│  ┌────────────▼─────────────┐  │
│  │  SimpleVectorStore       │  │
│  │  (In-Memory)             │  │
│  └────────────┬─────────────┘  │
│               │                 │
│  ┌────────────▼─────────────┐  │
│  │    EmbeddingModel        │  │
│  │  (LM Studio/OpenRouter)  │  │
│  └──────────────────────────┘  │
│                                 │
│  ┌──────────────────────────┐  │
│  │    ChatController        │  │
│  └────────────┬─────────────┘  │
│               │                 │
│  ┌────────────▼─────────────┐  │
│  │      RagService          │  │
│  │  - Similarity Search     │  │
│  │  - Context Building      │  │
│  │  - Answer Generation     │  │
│  └──────────────────────────┘  │
└─────────────────────────────────┘
```

## 🛠️ Tech Stack

### Backend
- **Spring Boot 3.3.0** - Application framework
- **Spring AI 1.0.0-M1** - AI integration
- **OpenAI API** - Chat completion (via OpenRouter)
- **Apache PDFBox 2.0.29** - PDF text extraction
- **Project Lombok** - Boilerplate reduction
- **Maven** - Dependency management

### Frontend
- **Vanilla JavaScript** - No framework dependencies
- **Modern CSS3** - Gradient backgrounds, animations
- **Fetch API** - HTTP communication

### AI Services
- **LM Studio** - Local embedding generation
- **OpenRouter** - Chat completion API
- **Model**: `mistralai/mistral-small-3.2-24b-instruct:free`
- **Embedding Model**: `text-embedding-nomic-embed-text-v2-moe`

## 📦 Prerequisites

1. **Java 17+** installed
2. **Maven 3.9+** installed
3. **LM Studio** running locally on port 1234 (for embeddings)
4. **OpenRouter API Key** (for chat completion)
5. 2GB+ RAM available

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd smart-faq-rag-assistant
```

### 2. Configure Environment Variables

Create a `.env` file or set environment variable:

```bash
export OPEN_ROUTER_KEY="your-openrouter-api-key-here"
```

Or on Windows:
```cmd
set OPEN_ROUTER_KEY=your-openrouter-api-key-here
```

### 3. Start LM Studio

1. Download and install [LM Studio](https://lmstudio.ai/)
2. Load the embedding model: `text-embedding-nomic-embed-text-v2-moe`
3. Start the local server on port 1234

### 4. Build and Run

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

Or on Windows:
```cmd
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

The application will start on `http://localhost:9090`

## ⚙️ Configuration

### `application.properties`

```properties
# Server Configuration
server.port=9090

# LM Studio (Local Embeddings)
spring.ai.openai.base-url=http://localhost:1234/
spring.ai.openai.api-key=""
spring.ai.openai.embedding.options.model=text-embedding-nomic-embed-text-v2-moe

# OpenRouter (Chat Completion)
spring.ai.openai.chat.base-url=https://openrouter.ai/api
spring.ai.openai.chat.api-key=${OPEN_ROUTER_KEY}
spring.ai.openai.chat.options.model=mistralai/mistral-small-3.2-24b-instruct:free

# File Upload Limits
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=10MB

# Preload FAQs on startup (optional)
app.preload.faqs=false
```

### Document Processing Configuration

Configured in `DocumentService.java`:

```java
private static final int CHUNK_SIZE = 500;           // Characters per chunk
private static final int CHUNK_OVERLAP = 100;        // Overlap between chunks
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
private static final int MAX_PDF_PAGES = 200;        // Max pages per PDF
private static final int BATCH_SIZE = 10;            // Chunks per batch
```

## 📖 Usage

### Web Interface

1. Open `http://localhost:9090` in your browser
2. Navigate through three tabs:
   - **📁 Upload**: Upload files, text, or FAQs
   - **💬 Chat**: Ask questions about uploaded documents
   - **📊 Stats**: View statistics and manage documents

### Upload Tab

#### Option 1: File Upload
- Click or drag-and-drop files (TXT, PDF, MD)
- Select single or multiple files
- Click "📤 Upload Files"

#### Option 2: Direct Text Input
- Enter a document title
- Paste text content
- Click "📝 Upload Text"

#### Option 3: FAQ Upload
- Enter FAQs (one per line)
- Format: `Q: Question? A: Answer.`
- Click "❓ Upload FAQs"

### Chat Tab

1. Type your question in the text area
2. Press Enter or click "🚀 Ask Question"
3. View AI-generated answers based on uploaded documents

### Stats Tab

- View total chunks stored
- See breakdown by document source
- Monitor memory usage
- Delete specific document sources
- Clear all documents

## 🔌 API Endpoints

### Health & System

```http
GET /api/chat/health
Response: {"status": "UP"}

GET /api/chat/memory
Response: {
  "maxMemoryMB": 2048,
  "totalMemoryMB": 512
}
```

### Document Upload

```http
POST /api/documents/upload
Content-Type: multipart/form-data
Body: file=@document.pdf

Response: {
  "message": "File processed successfully",
  "filename": "document.pdf",
  "chunksStored": 42
}
```

```http
POST /api/documents/upload/batch
Content-Type: multipart/form-data
Body: files[]=@doc1.pdf&files[]=@doc2.txt

Response: {
  "message": "Processed 2 of 2 files",
  "filesCount": 2,
  "totalChunksStored": 85
}
```

```http
POST /api/documents/upload/text
Content-Type: application/json
Body: {
  "text": "Your content here...",
  "title": "My Document"
}

Response: {
  "message": "Text processed successfully",
  "chunksStored": 15
}
```

```http
POST /api/documents/upload/faqs
Content-Type: application/json
Body: {
  "faqs": [
    "Q: What is AI? A: Artificial Intelligence...",
    "Q: What is ML? A: Machine Learning..."
  ]
}

Response: {
  "message": "FAQs processed successfully",
  "faqsStored": 2
}
```

### Document Management

```http
GET /api/documents/stats
Response: {
  "totalChunks": 127,
  "documentBreakdown": {
    "document.pdf": 42,
    "faqs": 25
  },
  "memoryUsedMB": 256,
  "memoryMaxMB": 2048,
  "timestamp": 1234567890
}

GET /api/documents/sources
Response: {
  "sources": ["document.pdf", "faqs", "user_text"],
  "count": 3
}

DELETE /api/documents/source/{sourceName}
Response: {
  "message": "Documents from source 'document.pdf' deleted successfully"
}

DELETE /api/documents/clear
Response: {
  "message": "All documents cleared successfully"
}
```

### Chat

```http
POST /api/chat
Content-Type: application/json
Body: {
  "question": "What is StarlightDB?"
}

Response: {
  "answer": "StarlightDB is a serverless graph database..."
}
```

## ⚠️ Current Limitations

### 1. **In-Memory Vector Store**
- **Problem**: All embeddings stored in JVM heap
- **Impact**: 
  - Maximum ~1000-2000 documents (depending on heap size)
  - Risk of OutOfMemoryError with large files
  - Data lost on restart
- **Workaround**: 
  - Increase heap: `-Xmx4g` or `-Xmx8g`
  - Limit file sizes (currently 10MB per file)
  - Process in batches

### 2. **JVM Heap Constraints**
- **Default**: 2GB max heap (`-Xmx2048m`)
- **Symptoms**:
  - Slow performance with many documents
  - HTTP 507 errors (Insufficient Storage)
  - Application crashes
- **Current Mitigation**:
  - Batch processing (10 chunks at a time)
  - Manual garbage collection after operations
  - 100ms delay between batches
  - File size validation

### 3. **PDF Processing**
- **Limits**:
  - Max 200 pages per PDF
  - Max 5MB extracted text
  - Entire PDF loaded into memory
- **Issues**:
  - Large PDFs can cause OOM
  - No streaming extraction
  - Complex PDFs with images consume more memory

### 4. **No Persistence**
- Documents lost on application restart
- No backup mechanism
- Cannot recover from crashes

### 5. **Single-Node Only**
- Cannot scale horizontally
- No load balancing
- Single point of failure

### 6. **Synchronous Processing**
- UI blocks during upload
- No background job processing
- Poor user experience with large files

### 7. **Limited Concurrency**
- `ReentrantLock` prevents parallel uploads
- One operation at a time
- No queue management

## 🚀 Future Improvements

### Recommended Production Architecture

```
┌──────────┐
│  Client  │
└────┬─────┘
     │
     ▼
┌─────────────────┐     ┌──────────────┐
│  Object Storage │◄────│  Presigned   │
│   (S3/MinIO)    │     │   URL API    │
└────┬────────────┘     └──────────────┘
     │
     │ Path/JobID
     ▼
┌─────────────────┐
│  Ingress API    │
│  (Spring Boot)  │
└────┬────────────┘
     │
     │ Enqueue Job
     ▼
┌─────────────────┐
│  Message Queue  │
│ (RabbitMQ/Kafka)│
└────┬────────────┘
     │
     │ Pull Job
     ▼
┌─────────────────┐     ┌──────────────┐
│ Ingestion Worker│────►│  Embedding   │
│   (Separate)    │     │   Service    │
└────┬────────────┘     └──────────────┘
     │
     │ Store Vectors
     ▼
┌─────────────────┐     ┌──────────────┐
│   Vector DB     │     │  Metadata DB │
│  (pgvector/     │◄───►│  (Postgres)  │
│   Milvus)       │     │              │
└─────────────────┘     └──────────────┘
```

### Phase 1: External Storage (MVP+)

**Goal**: Move off JVM heap

#### 1.1 Add PostgreSQL with pgvector
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**Schema**:
```sql
CREATE EXTENSION vector;

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    source VARCHAR(255),
    content TEXT,
    embedding vector(768), -- dimension depends on model
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX ON documents USING ivfflat (embedding vector_cosine_ops);
```

**Benefits**:
- ✅ No memory limits
- ✅ Persistent storage
- ✅ ACID transactions
- ✅ Full SQL capabilities

#### 1.2 Add Redis for Caching
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Cache frequently accessed embeddings and search results.

### Phase 2: Async Processing

#### 2.1 Add Message Queue
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**Implementation**:
```java
@Service
public class DocumentUploadService {
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    public String enqueueDocument(String s3Path, String title) {
        String jobId = UUID.randomUUID().toString();
        IngestionJob job = new IngestionJob(jobId, s3Path, title);
        rabbitTemplate.convertAndSend("document.ingestion", job);
        return jobId;
    }
}

@RabbitListener(queues = "document.ingestion")
public void processDocument(IngestionJob job) {
    // Stream from S3, chunk, embed, store
}
```

#### 2.2 Add Job Status Tracking
```sql
CREATE TABLE ingestion_jobs (
    job_id UUID PRIMARY KEY,
    status VARCHAR(20), -- PENDING, PROCESSING, DONE, FAILED
    progress INT,
    error_message TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**API**:
```http
GET /api/jobs/{jobId}
Response: {
  "jobId": "...",
  "status": "PROCESSING",
  "progress": 45,
  "totalChunks": 100
}
```

### Phase 3: Scalability

#### 3.1 Object Storage (MinIO/S3)
```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>
```

**Flow**:
1. Client requests presigned URL
2. Client uploads directly to S3/MinIO
3. Client notifies backend with S3 path
4. Backend enqueues ingestion job

**Benefits**:
- ✅ No file streaming through app
- ✅ Direct browser → storage
- ✅ Handles GB-sized files

#### 3.2 Separate Ingestion Workers

**Docker Compose**:
```yaml
services:
  api:
    image: rag-api
    ports:
      - "9090:9090"
    environment:
      - SPRING_PROFILES_ACTIVE=api
  
  worker:
    image: rag-worker
    deploy:
      replicas: 3
    environment:
      - SPRING_PROFILES_ACTIVE=worker
  
  postgres:
    image: ankane/pgvector
    
  minio:
    image: minio/minio
    
  rabbitmq:
    image: rabbitmq:management
```

#### 3.3 Streaming PDF Extraction

Replace Apache PDFBox with streaming alternative:

```java
public void extractPdfStreaming(InputStream input, Consumer<String> pageConsumer) {
    try (PDDocument doc = PDDocument.load(input)) {
        PDFTextStripper stripper = new PDFTextStripper();
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(doc);
            pageConsumer.accept(pageText); // Process immediately
        }
    }
}
```

### Phase 4: Advanced Features

#### 4.1 Hybrid Search
Combine vector similarity + keyword search:

```java
public List<Document> hybridSearch(String query) {
    // Vector search
    List<Document> vectorResults = vectorSearch(query, 10);
    
    // Keyword search (PostgreSQL full-text)
    List<Document> keywordResults = postgresFullTextSearch(query, 10);
    
    // Merge with RRF (Reciprocal Rank Fusion)
    return mergeResults(vectorResults, keywordResults);
}
```

#### 4.2 Metadata Filtering
```sql
SELECT * FROM documents
WHERE embedding <-> query_embedding < 0.5
  AND metadata->>'source' = 'user_manual'
  AND metadata->>'category' = 'installation'
ORDER BY embedding <-> query_embedding
LIMIT 5;
```

#### 4.3 Reranking
Add a reranker model to improve relevance:

```java
public List<Document> rerankResults(String query, List<Document> candidates) {
    // Use cross-encoder model to rerank
    return rerankerService.rerank(query, candidates);
}
```

#### 4.4 Multi-tenancy
```sql
ALTER TABLE documents ADD COLUMN tenant_id UUID;
CREATE INDEX ON documents(tenant_id);
```

#### 4.5 Observability
- **Metrics**: Micrometer + Prometheus
- **Tracing**: OpenTelemetry + Jaeger
- **Logging**: ELK Stack

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Migration Roadmap

| Phase | Time | Effort | Impact |
|-------|------|--------|--------|
| Phase 1 (Postgres + pgvector) | 1-2 weeks | Medium | High - Removes memory limits |
| Phase 2 (Async + Queue) | 2-3 weeks | Medium | High - Better UX |
| Phase 3 (S3 + Workers) | 3-4 weeks | High | High - True scalability |
| Phase 4 (Advanced) | Ongoing | High | Medium - Nice-to-haves |

## 🐛 Troubleshooting

### OutOfMemoryError

**Symptoms**:
- HTTP 507 errors
- Application crashes
- Slow performance

**Solutions**:
1. Increase heap:
   ```bash
   export MAVEN_OPTS="-Xmx4g"
   ./mvnw spring-boot:run
   ```

2. Reduce batch size in `DocumentService`:
   ```java
   private static final int BATCH_SIZE = 5; // Reduce from 10
   ```

3. Clear documents regularly via `/api/documents/clear`

### LM Studio Connection Errors

**Symptoms**:
- "API connection error"
- "Error connecting to AI service"

**Solutions**:
1. Verify LM Studio is running: `http://localhost:1234`
2. Check model is loaded in LM Studio
3. Verify base URL in `application.properties`

### OpenRouter Authentication Failed

**Symptoms**:
- "API authentication failed"

**Solutions**:
1. Verify `OPEN_ROUTER_KEY` environment variable is set
2. Check API key is valid at [OpenRouter Dashboard](https://openrouter.ai/)
3. Ensure sufficient credits in OpenRouter account

### PDF Extraction Fails

**Symptoms**:
- "PDF too large to process"
- "Out of memory while processing PDF"

**Solutions**:
1. Reduce PDF size (split into smaller files)
2. Increase `MAX_PDF_PAGES` limit
3. Extract text externally and use text upload

## 🤝 Contributing

Contributions welcome! Areas needing help:

1. **Database Integration**: Implement pgvector support
2. **Async Processing**: Add message queue
3. **Object Storage**: S3/MinIO integration
4. **Testing**: Unit and integration tests
5. **Documentation**: API docs, tutorials
6. **UI/UX**: Improve frontend

## 📄 License

[MIT LICENSE](https://github.com/sanidhyasakhuja/SmartFaqRag#MIT-1-ov-file)

## 👤 Author

Sanidhya Sakhuja (sanidhyasakhuja@gmail.com)

---

**Note**: This is a development/MVP implementation. For production use, implement the recommended architecture with external storage, async processing, and proper scalability measures outlined in the Future Improvements section.
