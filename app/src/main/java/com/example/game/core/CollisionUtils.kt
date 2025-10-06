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
}
