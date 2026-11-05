package com.example

import ai.onnxruntime.OrtEnvironment
import org.junit.Test
import java.io.File
import org.tensorflow.lite.Interpreter

class ModelValidationTest {
    @Test
    fun testModelSignatures() {
        println("=== MODEL VALIDATION ===")
        val env = OrtEnvironment.getEnvironment()
        
        val lamaPath = "src/main/assets/models/inpainting_lama_2025jan.onnx"
        val esrganPath = "src/main/assets/models/RealESRGAN_x2plus.onnx"
        val cpgaPath = "src/main/assets/models/cpga_fp16.tflite"
        
        try {
            val lamaFile = File(lamaPath)
            println("LaMa File Size: " + lamaFile.length())
            if (lamaFile.exists() && lamaFile.length() > 1000) {
                val session = env.createSession(lamaFile.absolutePath)
                println("LaMa Inputs: " + session.inputNames.map { it + " " + session.inputInfo[it]?.info.toString() })
                println("LaMa Outputs: " + session.outputNames.map { it + " " + session.outputInfo[it]?.info.toString() })
                session.close()
            }
        } catch (e: Exception) {
            println("LaMa Error: " + e.message)
        }

        try {
            val esrganFile = File(esrganPath)
            println("RealESRGAN File Size: " + esrganFile.length())
            if (esrganFile.exists() && esrganFile.length() > 1000) {
                val session = env.createSession(esrganFile.absolutePath)
                println("RealESRGAN Inputs: " + session.inputNames.map { it + " " + session.inputInfo[it]?.info.toString() })
                println("RealESRGAN Outputs: " + session.outputNames.map { it + " " + session.outputInfo[it]?.info.toString() })
                session.close()
            }
        } catch (e: Exception) {
            println("RealESRGAN Error: " + e.message)
        }
        
        try {
            val cpgaFile = File(cpgaPath)
            println("CPGA File Size: " + cpgaFile.length())
            if (cpgaFile.exists() && cpgaFile.length() > 1000) {
                val interpreter = Interpreter(cpgaFile)
                val inputCount = interpreter.getInputTensorCount()
                for (i in 0 until inputCount) {
                    println("CPGA Input $i: " + interpreter.getInputTensor(i).shape().contentToString())
                }
                val outputCount = interpreter.getOutputTensorCount()
                for (i in 0 until outputCount) {
                    println("CPGA Output $i: " + interpreter.getOutputTensor(i).shape().contentToString())
                }
                interpreter.close()
            }
        } catch (e: Exception) {
            println("CPGA Error: " + e.message)
        }
        println("=== END MODEL VALIDATION ===")
    }
}
