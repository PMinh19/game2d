package com.example.game.core

import android.graphics.RectF

object CollisionUtils {
    fun checkCollisionPlaneMonster(
        planeX: Float,
        planeY: Float,
        planeWidth: Float,
        planeHeight: Float,
        monster: BaseMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)
        val monsterRect = RectF(monster.x, monster.y.value, monster.x + 80f, monster.y.value + 80f)
        return RectF.intersects(planeRect, monsterRect)
    }

    fun checkCollisionPlaneCoin(
        planeX: Float,
        planeY: Float,
        planeWidth: Float,
        planeHeight: Float,
        coin: BaseCoin
    ): Boolean {
        val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)
        val coinRect = RectF(coin.x, coin.y.value, coin.x + 40f, coin.y.value + 40f)
        return RectF.intersects(planeRect, coinRect)
    }

    fun checkCollisionBulletMonster(bullet: Bullet, monster: BaseMonster): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val bulletRect = RectF(bullet.x, bullet.y, bullet.x + 30f, bullet.y + 30f)
        val monsterRect = RectF(monster.x, monster.y.value, monster.x + 80f, monster.y.value + 80f)
        return RectF.intersects(bulletRect, monsterRect)
    }

    /**
     * Check collision between wall and monster
     * Wall is positioned at planeY - 60f with height 40dp (~120px)
     */
    fun checkCollisionWallMonster(
        planeY: Float,
        monster: BaseMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val wallTop = planeY - 60f
        val wallBottom = wallTop + 120f // ~40dp height
        val monsterBottom = monster.y.value + 80f

        // Check if monster is in wall's vertical range
        return monsterBottom >= wallTop && monster.y.value <= wallBottom
    }

    /**
     * Check collision between bullet and GrowingMonster with dynamic size
     */
    fun checkCollisionBulletGrowingMonster(bullet: Bullet, monster: GrowingMonster): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val bulletRect = RectF(bullet.x, bullet.y, bullet.x + 30f, bullet.y + 30f)

        // Calculate monster bounds with dynamic size
        val monsterLeft = monster.x - (monster.currentSize.value - 80f) / 2
        val monsterRect = RectF(
            monsterLeft,
            monster.y.value,
            monsterLeft + monster.currentSize.value,
            monster.y.value + monster.currentSize.value
        )
        return RectF.intersects(bulletRect, monsterRect)
    }

    /**
     * Check collision between plane and GrowingMonster with dynamic size
     */
    fun checkCollisionPlaneGrowingMonster(
        planeX: Float,
        planeY: Float,
        planeWidth: Float,
        planeHeight: Float,
        monster: GrowingMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)

        // Calculate monster bounds with dynamic size
        val monsterLeft = monster.x - (monster.currentSize.value - 80f) / 2
        val monsterRect = RectF(
            monsterLeft,
            monster.y.value,
            monsterLeft + monster.currentSize.value,
            monster.y.value + monster.currentSize.value
        )
        return RectF.intersects(planeRect, monsterRect)
    }

    /**
     * Check collision between bullet and InvisibleMonster
     */
    fun checkCollisionBulletInvisibleMonster(bullet: Bullet, monster: InvisibleMonster): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val bulletRect = RectF(bullet.x, bullet.y, bullet.x + 30f, bullet.y + 30f)
        val monsterRect = RectF(monster.x, monster.y.value, monster.x + 80f, monster.y.value + 80f)
        return RectF.intersects(bulletRect, monsterRect)
    }

    /**
     * Check collision between plane and InvisibleMonster
     */
    fun checkCollisionPlaneInvisibleMonster(
        planeX: Float,
        planeY: Float,
        planeWidth: Float,
        planeHeight: Float,
        monster: InvisibleMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)
        val monsterRect = RectF(monster.x, monster.y.value, monster.x + 80f, monster.y.value + 80f)
        return RectF.intersects(planeRect, monsterRect)
    }

    /**
     * Check collision between bullet and SplittingMonster with dynamic size
     */
    fun checkCollisionBulletSplittingMonster(bullet: Bullet, monster: SplittingMonster): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val bulletRect = RectF(bullet.x, bullet.y, bullet.x + 30f, bullet.y + 30f)
        val monsterRect = RectF(
            monster.x,
            monster.y.value,
            monster.x + monster.size,
            monster.y.value + monster.size
        )
        return RectF.intersects(bulletRect, monsterRect)
    }

    /**
     * Check collision between plane and SplittingMonster with dynamic size
     */
    fun checkCollisionPlaneSplittingMonster(
        planeX: Float,
        planeY: Float,
        planeWidth: Float,
        planeHeight: Float,
        monster: SplittingMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)
        val monsterRect = RectF(
            monster.x,
            monster.y.value,
            monster.x + monster.size,
            monster.y.value + monster.size
        )
        return RectF.intersects(planeRect, monsterRect)
    }

    /**
     * Check collision between wall and GrowingMonster with dynamic size
     */
    fun checkCollisionWallGrowingMonster(
        planeY: Float,
        monster: GrowingMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val wallTop = planeY - 60f
        val wallBottom = wallTop + 120f
        val wallRect = RectF(0f, wallTop, Float.MAX_VALUE, wallBottom)

        val monsterLeft = monster.x - (monster.currentSize.value - 80f) / 2
        val monsterRect = RectF(
            monsterLeft,
            monster.y.value,
            monsterLeft + monster.currentSize.value,
            monster.y.value + monster.currentSize.value
        )
        return RectF.intersects(wallRect, monsterRect)
    }

    /**
     * Check collision between wall and InvisibleMonster
     */
    fun checkCollisionWallInvisibleMonster(
        planeY: Float,
        monster: InvisibleMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val wallTop = planeY - 60f
        val wallBottom = wallTop + 120f
        val monsterBottom = monster.y.value + 80f
        return monsterBottom >= wallTop && monster.y.value <= wallBottom
    }

    /**
     * Check collision between wall and SplittingMonster
     */
    fun checkCollisionWallSplittingMonster(
        planeY: Float,
        monster: SplittingMonster
    ): Boolean {
        if (!monster.alive.value || monster.hp.value <= 0) return false
        val wallTop = planeY - 60f
        val wallBottom = wallTop + 120f
        val wallRect = RectF(0f, wallTop, Float.MAX_VALUE, wallBottom)

        val monsterRect = RectF(
            monster.x,
            monster.y.value,
            monster.x + monster.size,
            monster.y.value + monster.size
        )
        return RectF.intersects(wallRect, monsterRect)
    }
}
