package com.hitster.animations

data class AnimationSpec(
    val durationMillis: Long,
    val easing: String,
)

data class AnimationCatalog(
    val draw: AnimationSpec,
    val move: AnimationSpec,
    val snap: AnimationSpec,
    val reveal: AnimationSpec,
    val validation: AnimationSpec,
) {
    companion object {
        fun default(): AnimationCatalog {
            return AnimationCatalog(
                draw = AnimationSpec(durationMillis = 280, easing = "easeOutQuad"),
                move = AnimationSpec(durationMillis = 140, easing = "linear"),
                snap = AnimationSpec(durationMillis = 180, easing = "easeOutBack"),
                reveal = AnimationSpec(durationMillis = 240, easing = "easeOutCubic"),
                validation = AnimationSpec(durationMillis = 420, easing = "easeInOutCubic"),
            )
        }
    }
}

enum class GameAnimationCue {
    DRAW_FROM_DECK,
    MOVE_PENDING_CARD,
    SNAP_TO_SLOT,
    REVEAL_CARD,
    VALIDATE_PLACEMENT,
}
