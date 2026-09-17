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
    fun `brightness request creates brightness action`() = runBlocking {
        val result = planner.createPlan("make it brighter", context)
        assertTrue(result is AssistantResult.Planned)
        val actions = (result as AssistantResult.Planned).plan.actions
        assertEquals(1, actions.size)
        assertTrue(actions[0] is AssistantAction.AdjustBrightness)
    }

    @Test
    fun `contrast request creates contrast action`() = runBlocking {
        val result = planner.createPlan("increase contrast", context)
        assertTrue(result is AssistantResult.Planned)
        val actions = (result as AssistantResult.Planned).plan.actions
        assertEquals(1, actions.size)
        assertTrue(actions[0] is AssistantAction.AdjustContrast)
    }

    @Test
    fun `saturation request creates saturation action`() = runBlocking {
        val result = planner.createPlan("increase saturation", context)
        assertTrue(result is AssistantResult.Planned)
        val actions = (result as AssistantResult.Planned).plan.actions
        assertEquals(1, actions.size)
        assertTrue(actions[0] is AssistantAction.AdjustSaturation)
    }

    @Test
    fun `temperature request creates temperature action`() = runBlocking {
        val result = planner.createPlan("make it warmer", context)
        assertTrue(result is AssistantResult.Planned)
        val actions = (result as AssistantResult.Planned).plan.actions
        assertEquals(1, actions.size)
        assertTrue(actions[0] is AssistantAction.AdjustTemperature)
    }

    @Test
    fun `background removal request creates remove background action`() = runBlocking {
        val result = planner.createPlan("remove background", context)
        assertTrue(result is AssistantResult.Planned)
        val plan = (result as AssistantResult.Planned).plan
        assertEquals(1, plan.actions.size)
        assertTrue(plan.actions[0] is AssistantAction.RemoveBackground)
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `multi action prompt creates multiple actions`() = runBlocking {
        val result = planner.createPlan("make it brighter and warmer", context)
        assertTrue(result is AssistantResult.Planned)
        val plan = (result as AssistantResult.Planned).plan
        assertEquals(2, plan.actions.size)
        assertTrue(plan.actions.any { it is AssistantAction.AdjustBrightness })
        assertTrue(plan.actions.any { it is AssistantAction.AdjustTemperature })
    }

    @Test
    fun `unsupported request returns unsupported`() = runBlocking {
        val result = planner.createPlan("turn this into a poster", context)
        assertTrue(result is AssistantResult.Unsupported)
    }
}
