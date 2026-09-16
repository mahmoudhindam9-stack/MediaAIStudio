import os

path = "app/src/main/java/com/example/videoeditor/VideoEditorViewModel.kt"
with open(path, "r") as f:
    content = f.read()

if "GenerativeEngine" not in content:
    content = content.replace("import com.example.ai.video.*", "import com.example.ai.video.*\nimport com.example.ai.generative.*")
    
    insert_point = "private val aiEngine = AIVideoEngine(application)"
    replacement = insert_point + "\n    val generativeEngine = GenerativeEngine(application)"
    content = content.replace(insert_point, replacement)

    methods = """
    fun runGenerativeVideo(type: GenerativeType, prompt: String) {
        val s = _state.value
        val uri = s.videoClips.firstOrNull()?.uri
        if (uri == null) {
            viewModelScope.launch { _aiMessages.emit("No source video for generation") }
            return
        }
        
        val req = GenerativeRequest(
            type = type,
            sourceUri = android.net.Uri.parse(uri),
            prompt = prompt
        )
        val jobId = generativeEngine.submitJob(req)
        viewModelScope.launch {
            _aiMessages.emit("Generative Job $jobId submitted.")
            // Monitor job state
            generativeEngine.jobs.collect { jobs ->
                val job = jobs[jobId]
                if (job != null) {
                    if (job.state == JobState.FAILED) {
                        _aiMessages.emit("Generation Failed: ${job.message}")
                    } else if (job.state == JobState.COMPLETED) {
                        _aiMessages.emit("Generation Completed!")
                    }
                }
            }
        }
    }
    """
    
    content = content.replace("override fun onCleared()", methods + "\n    override fun onCleared()")
    
    with open(path, "w") as f:
        f.write(content)

