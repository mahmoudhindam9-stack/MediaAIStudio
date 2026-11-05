import os

path = "app/src/main/java/com/example/videoeditor/VideoEditorViewModel.kt"
with open(path, "r") as f:
    content = f.read()

if "com.example.ai.video.AIVideoEngine" not in content:
    content = content.replace(
        "import java.util.UUID",
        "import java.util.UUID\nimport com.example.ai.video.*\nimport kotlinx.coroutines.flow.MutableSharedFlow\nimport kotlinx.coroutines.flow.SharedFlow\nimport kotlinx.coroutines.flow.asSharedFlow"
    )
    
    # Add aiEngine
    insert_point = "val exoPlayer = ExoPlayer.Builder(application).build()"
    ai_engine = """val exoPlayer = ExoPlayer.Builder(application).build()
    private val aiEngine = AIVideoEngine(application)
    
    private val _aiMessages = MutableSharedFlow<String>()
    val aiMessages = _aiMessages.asSharedFlow()
    """
    content = content.replace(insert_point, ai_engine)
    
    # Add AI methods
    methods = """
    fun runObjectTracking() {
        val s = _state.value
        val uri = s.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit("Running Object Tracking...")
            val result = aiEngine.tracking.analyze(uri)
            if (result is VideoAnalysisResult.Tracking) {
                updateState(s.copy(aiTrackingData = result.keyframes))
                commitState()
                _aiMessages.emit("Tracking Complete")
            } else if (result is VideoAnalysisResult.Error) {
                _aiMessages.emit(result.message)
            }
        }
    }

    fun runSmartCut() {
        val s = _state.value
        val uri = s.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit("Analyzing for Smart Cuts...")
            val result = aiEngine.smartCut.analyze(uri)
            if (result is VideoAnalysisResult.SmartCuts) {
                updateState(s.copy(aiSuggestedCuts = result.suggestedCuts))
                // We do NOT commit state here to avoid auto-applying. User must review.
                _aiMessages.emit("Smart Cuts Suggested")
            } else if (result is VideoAnalysisResult.Error) {
                _aiMessages.emit(result.message)
            }
        }
    }
    
    fun applySmartCuts() {
        val s = _state.value
        val cuts = s.aiSuggestedCuts ?: return
        // Simplify: Just remove the start and end according to cuts
        // In a real editor, this would slice the timeline. For this demo, we trim the first clip.
        val clip = s.videoClips.firstOrNull() ?: return
        
        var newStart = clip.startTrimMs
        var newDuration = clip.durationMs
        cuts.forEach { cut ->
            if (cut.startTimeMs == 0L) {
                newStart = cut.endTimeMs
                newDuration -= cut.endTimeMs
            } else if (cut.endTimeMs >= clip.originalDurationMs - 1000L) {
                newDuration -= (cut.endTimeMs - cut.startTimeMs)
            }
        }
        
        val newClip = clip.copy(startTrimMs = newStart, durationMs = newDuration)
        updateState(s.copy(videoClips = listOf(newClip), aiSuggestedCuts = null))
        commitState()
        updatePlayerMedia()
    }
    
    fun rejectSmartCuts() {
        updateState(_state.value.copy(aiSuggestedCuts = null))
    }

    fun runAutoCaptions() {
        val s = _state.value
        val uri = s.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit("Generating Captions...")
            val result = aiEngine.autoCaption.generate(uri)
            if (result is VideoAnalysisResult.AutoCaptions) {
                updateState(s.copy(aiSubtitleTrack = result.track))
                commitState()
                _aiMessages.emit("Captions Generated")
            } else if (result is VideoAnalysisResult.Error) {
                _aiMessages.emit(result.message)
            }
        }
    }

    fun runSmartReframe() {
        val s = _state.value
        val uri = s.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit("Generating Reframe Paths...")
            val result = aiEngine.smartReframe.process(uri)
            if (result is VideoAnalysisResult.SmartReframe) {
                _aiMessages.emit("Smart Reframe generated ${result.cropPaths.size} paths.")
            } else if (result is VideoAnalysisResult.Error) {
                _aiMessages.emit(result.message)
            }
        }
    }

    fun runEnhancement() {
        val s = _state.value
        val uri = s.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit("Enhancing Video...")
            val result = aiEngine.enhancement.enhance(uri)
            if (result is VideoAnalysisResult.Error) {
                _aiMessages.emit(result.message)
            }
        }
    }
    """
    
    content = content.replace("override fun onCleared()", methods + "\n    override fun onCleared()")
    with open(path, "w") as f:
        f.write(content)
