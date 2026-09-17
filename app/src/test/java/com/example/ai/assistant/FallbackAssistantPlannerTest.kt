package com.example.ai.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackAssistantPlannerTest {

    private val planner = FallbackAssistantPlanner()
    private val context = AssistantContext(
        mediaType = MediaType.PHOTO,
        hasSourceMedia = true
    )

    @Test
    fun `empty prompt returns unsupported`() = runBlocking {
        val result = planner.createPlan("   ", context)
        assertTrue(result is AssistantResult.Unsupported)
    }

    @Test
    fun `brightness request uses effective editor range`() = runBlocking {
        val result = planner.createPlan("make it brighter", context)
        assertTrue(result is AssistantResult.Planned)
        val action = (result as AssistantResult.Planned).plan.actions.single()
        assertEquals(25f, (action as AssistantAction.AdjustBrightness).amount, 0.001f)
    }

    @Test
    fun `darker request produces negative brightness`() = runBlocking {
        val result = planner.createPlan("make it darker", context)
        assertTrue(result is AssistantResult.Planned)
        val action = (result as AssistantResult.Planned).plan.actions.single()
        assertEquals(-25f, (action as AssistantAction.AdjustBrightness).amount, 0.001f)
    }

    @Test
    fun `contrast request creates contrast action`() = runBlocking {
        val result = planner.createPlan("increase contrast", context)
        assertTrue(result is AssistantResult.Planned)
        val action = (result as AssistantResult.Planned).plan.actions.single()
        assertEquals(0.10f, (action as AssistantAction.AdjustContrast).amount, 0.001f)
    }

    @Test
    fun `saturation request creates saturation action`() = runBlocking {
        val result = planner.createPlan("increase saturation", context)
        assertTrue(result is AssistantResult.Planned)
        val action = (result as AssistantResult.Planned).plan.actions.single()
        assertEquals(0.10f, (action as AssistantAction.AdjustSaturation).amount, 0.001f)
    }

    @Test
    fun `temperature request creates temperature action`() = runBlocking {
        val result = planner.createPlan("make it warmer", context)
        assertTrue(result is AssistantResult.Planned)
        val action = (result as AssistantResult.Planned).plan.actions.single()
        assertEquals(0.10f, (action as AssistantAction.AdjustTemperature).amount, 0.001f)
    }

    @Test
    fun `arabic request creates multiple actions`() = runBlocking {
        val result = planner.createPlan("زود الإضاءة وخلي الألوان أقوى وخلي الصورة دافئة", context)
        assertTrue(result is AssistantResult.Planned)
        val plan = (result as AssistantResult.Planned).plan
        assertEquals(3, plan.actions.size)
        assertTrue(plan.actions.any { it is AssistantAction.AdjustBrightness })
        assertTrue(plan.actions.any { it is AssistantAction.AdjustSaturation })
        assertTrue(plan.actions.any { it is AssistantAction.AdjustTemperature })
    }

    @Test
    fun `background removal requires confirmation`() = runBlocking {
        val result = planner.createPlan("remove background", context)
        assertTrue(result is AssistantResult.Planned)
        val plan = (result as AssistantResult.Planned).plan
        assertTrue(plan.requiresConfirmation)
        assertTrue(plan.actions.single() is AssistantAction.RemoveBackground)
    }

    @Test
    fun `assistant converts confirmation plan into confirmation result`() = runBlocking {
        val result = AIAssistant(planner).plan("remove background", context)
        assertTrue(result is AssistantResult.NeedsConfirmation)
        assertTrue((result as AssistantResult.NeedsConfirmation).plan.requiresConfirmation)
    }

    @Test
    fun `undo and redo are recognized`() = runBlocking {
        val undo = planner.createPlan("undo", context)
        val redo = planner.createPlan("redo", context)
        assertTrue(undo is AssistantResult.Planned)
        assertTrue(redo is AssistantResult.Planned)
        assertTrue((undo as AssistantResult.Planned).plan.actions.single() is AssistantAction.Undo)
        assertTrue((redo as AssistantResult.Planned).plan.actions.single() is AssistantAction.Redo)
    }

    @Test
    fun `unsupported request returns unsupported`() = runBlocking {
        val result = planner.createPlan("turn this into a poster", context)
        assertTrue(result is AssistantResult.Unsupported)
    }
}
