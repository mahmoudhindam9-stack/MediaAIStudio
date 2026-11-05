import os

path = "app/src/main/java/com/example/photoeditor/PhotoEditorViewModel.kt"
with open(path, "r") as f:
    content = f.read()

if "GenerativeEngine" not in content:
    content = content.replace("import com.example.ai.assistant.*", "import com.example.ai.assistant.*\nimport com.example.ai.generative.*")
    
    insert_point = "val aiEngine = AIImageEngine(AIProviderManager(application))"
    replacement = insert_point + "\n    val generativeEngine = GenerativeEngine(application)"
    content = content.replace(insert_point, replacement)
    
    methods = """
    fun runGenerativeImage(type: GenerativeType, prompt: String, maskUri: String? = null) {
        val s = state.value
        val uri = s.currentImageUri ?: return
        val req = GenerativeRequest(
            type = type,
            sourceUri = Uri.parse(uri),
            maskUri = maskUri?.let { Uri.parse(it) },
            prompt = prompt
        )
        val jobId = generativeEngine.submitJob(req)
        viewModelScope.launch {
            generativeEngine.jobs.collect { jobs ->
                val job = jobs[jobId]
                if (job != null) {
                    if (job.state == JobState.FAILED) {
                        aiError.value = job.message
                    } else if (job.state == JobState.PROCESSING || job.state == JobState.PREPARING) {
                        aiProgress.value = AIProgress(job.progress, job.message)
                    } else if (job.state == JobState.COMPLETED) {
                        aiProgress.value = null
                        val res = job.result
                        if (res is GenerativeResult.Success) {
                            previewAiResultUri.value = res.outputUri.toString()
                        }
                    }
                }
            }
        }
    }
    """
    
    # insert before the last brace. We can find 'fun setAiMode' and just append it before it? No, just replace last '}'
    content = content.rstrip()
    if content.endswith("}"):
        content = content[:-1] + methods + "\n}"
        
    with open(path, "w") as f:
        f.write(content)

