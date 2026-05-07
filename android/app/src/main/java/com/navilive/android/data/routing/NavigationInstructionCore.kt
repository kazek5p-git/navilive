package com.navilive.android.data.routing

import com.navilive.android.model.RouteStep
import com.navilive.android.model.RouteStepKind
import com.navilive.android.model.SharedProductRules
import java.util.Locale

internal data class NavigationInstructionDescriptor(
    val strategy: Strategy,
    val roadName: String? = null,
    val normalizedModifier: String? = null,
) {
    enum class Strategy {
        DepartNamed,
        Arrive,
        TurnNamed,
        TurnGenericNamed,
        TurnBareModifier,
        ContinueNamed,
        ProceedTowardNamed,
    }

    fun paritySignature(): String {
        val road = roadName ?: "-"
        val modifier = normalizedModifier ?: "-"
        return "${strategy.name}|$road|$modifier"
    }
}

internal object NavigationInstructionCore {

    fun describe(
        maneuverType: String,
        modifier: String?,
        roadName: String?,
    ): NavigationInstructionDescriptor {
        val normalizedRoad = roadName?.trim().orEmpty().ifBlank { null }
        val normalizedModifier = modifier
            ?.let(SharedProductRules.Instructions::normalizeModifier)
            ?.takeIf { it in SharedProductRules.Instructions.supportedModifiers }

        return when (maneuverType) {
            "depart" -> NavigationInstructionDescriptor(
                strategy = NavigationInstructionDescriptor.Strategy.DepartNamed,
                roadName = normalizedRoad,
            )
            "arrive" -> NavigationInstructionDescriptor(
                strategy = NavigationInstructionDescriptor.Strategy.Arrive,
            )
            "turn" -> turnDescriptor(normalizedRoad, normalizedModifier)
            "new name", "continue" -> NavigationInstructionDescriptor(
                strategy = NavigationInstructionDescriptor.Strategy.ContinueNamed,
                roadName = normalizedRoad,
            )
            else -> if (normalizedModifier != null) {
                turnDescriptor(normalizedRoad, normalizedModifier)
            } else {
                NavigationInstructionDescriptor(
                    strategy = NavigationInstructionDescriptor.Strategy.ProceedTowardNamed,
                    roadName = normalizedRoad,
                )
            }
        }
    }

    private fun turnDescriptor(
        normalizedRoad: String?,
        normalizedModifier: String?,
    ): NavigationInstructionDescriptor {
        return when {
            normalizedRoad != null && normalizedModifier != null ->
                NavigationInstructionDescriptor(
                    strategy = NavigationInstructionDescriptor.Strategy.TurnNamed,
                    roadName = normalizedRoad,
                    normalizedModifier = normalizedModifier,
                )
            normalizedRoad != null ->
                NavigationInstructionDescriptor(
                    strategy = NavigationInstructionDescriptor.Strategy.TurnGenericNamed,
                    roadName = normalizedRoad,
                )
            normalizedModifier != null ->
                NavigationInstructionDescriptor(
                    strategy = NavigationInstructionDescriptor.Strategy.TurnBareModifier,
                    normalizedModifier = normalizedModifier,
                )
            else ->
                NavigationInstructionDescriptor(
                    strategy = NavigationInstructionDescriptor.Strategy.TurnGenericNamed,
                )
        }
    }
}

internal object RouteStepSimplificationCore {
    private const val SHORT_CONNECTOR_STEP_MAX_METERS = 45

    fun shouldSuppressRouteStep(
        step: RouteStep,
        previous: RouteStep?,
        index: Int,
        lastIndex: Int,
    ): Boolean {
        if (previous == null || index == 0 || index == lastIndex) return false
        if (step.kind != RouteStepKind.Instruction || previous.kind != RouteStepKind.Instruction) return false
        if (step.maneuverType.equals("arrive", ignoreCase = true)) return false
        val currentRoad = normalizedRouteRoadName(step.roadName)
        val previousRoad = normalizedRouteRoadName(previous.roadName)
        val isSameRoad = currentRoad != null && currentRoad == previousRoad
        val isShortConnector = step.distanceMeters <= SHORT_CONNECTOR_STEP_MAX_METERS
        if (isTurnLikeManeuver(step)) {
            return isShortConnector && (currentRoad == null || isSameRoad)
        }
        if (currentRoad == null) return isShortConnector
        if (currentRoad != previousRoad) return false
        return step.distanceMeters <= 35
    }

    fun isTurnLikeManeuver(step: RouteStep): Boolean {
        val type = step.maneuverType?.lowercase(Locale.ROOT).orEmpty()
        val modifier = step.maneuverModifier
            ?.let(SharedProductRules.Instructions::normalizeModifier)
            .orEmpty()
        if (modifier == "straight") return false
        if (modifier in SharedProductRules.Instructions.supportedModifiers) return true
        return type in setOf(
            "turn",
            "end of road",
            "fork",
            "merge",
            "on ramp",
            "off ramp",
            "roundabout turn",
            "exit roundabout",
            "rotary",
            "roundabout",
        )
    }

    private fun normalizedRouteRoadName(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
}
