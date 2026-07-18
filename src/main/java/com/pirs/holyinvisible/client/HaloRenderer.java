package com.pirs.holyinvisible.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Рисует объёмный красный нимб (тор) над головой невидимых игроков.
 *
 * Рендер идёт через обычный VertexConsumerProvider с RenderLayer, который
 * сам управляет тестом глубины, поэтому нимб автоматически перекрывается
 * блоками (не виден сквозь стены) и при этом НЕ показывает силуэт/модель
 * самого игрока — только сам нимб. Этот способ не завязан на внутренние
 * шейдерные классы, которые Mojang неоднократно переименовывала между
 * версиями, поэтому он более устойчив к обновлениям игры.
 */
public final class HaloRenderer {

	private static final float RING_RADIUS = 0.36f;
	private static final float TUBE_RADIUS = 0.055f;
	private static final float HEIGHT_ABOVE_HEAD = 0.5f;
	private static final int SEGMENTS = 48;
	private static final int TUBE_SEGMENTS = 10;

	private HaloRenderer() {
	}

	public static void render(WorldRenderContext context) {
		if (!HolyConfig.isEnabled()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}

		VertexConsumerProvider.Immediate consumers = context.consumers();
		if (consumers == null) {
			return;
		}

		List<AbstractClientPlayerEntity> players = client.world.getPlayers();
		if (players.isEmpty()) {
			return;
		}

		Vec3d cameraPos = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		float tickDelta = context.tickCounter().getTickDelta(true);

		// Лёгкая анимация: вращение и пульсация свечения по времени.
		long time = client.world.getTime();
		float rotation = (time + tickDelta) * 1.5f;
		float pulse = 0.75f + 0.25f * MathHelper.sin((time + tickDelta) * 0.08f);

		boolean rendered = false;

		for (AbstractClientPlayerEntity player : players) {
			if (!shouldRenderHalo(player, client)) {
				continue;
			}

			double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - cameraPos.x;
			double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - cameraPos.y;
			double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - cameraPos.z;

			float headHeight = player.getStandingEyeHeight() + 0.15f;

			matrices.push();
			matrices.translate(x, y + headHeight + HEIGHT_ABOVE_HEAD, z);
			matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(rotation));

			drawHalo(matrices, consumers, pulse);
			rendered = true;

			matrices.pop();
		}

		if (rendered) {
			consumers.draw(RenderLayer.getDebugQuads());
		}
	}

	private static boolean shouldRenderHalo(AbstractClientPlayerEntity player, MinecraftClient client) {
		if (player == client.cameraEntity) {
			// Себя не подсвечиваем от первого лица - смысла нет.
			return false;
		}
		if (player.isSpectator()) {
			return false;
		}
		// Показываем нимб только когда игрок реально невидим для нас.
		return player.isInvisible();
	}

	private static void drawHalo(MatrixStack matrices, VertexConsumerProvider consumers, float pulse) {
		Matrix4f model = matrices.peek().getPositionMatrix();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugQuads());

		int glowAlpha = (int) (170 + 60 * pulse);
		int glowCoreAlpha = (int) (90 * pulse);

		// Мягкое красное свечение под нимбом (кольцо из вырожденных квадов,
		// сходящихся к центру) - даёт объём и "ауру" без силуэта игрока.
		for (int i = 0; i < SEGMENTS; i++) {
			double angle1 = 2 * Math.PI * i / SEGMENTS;
			double angle2 = 2 * Math.PI * (i + 1) / SEGMENTS;

			float outerX1 = (float) (Math.cos(angle1) * RING_RADIUS * 1.6f);
			float outerZ1 = (float) (Math.sin(angle1) * RING_RADIUS * 1.6f);
			float outerX2 = (float) (Math.cos(angle2) * RING_RADIUS * 1.6f);
			float outerZ2 = (float) (Math.sin(angle2) * RING_RADIUS * 1.6f);

			buffer.vertex(model, 0f, 0.01f, 0f).color(255, 60, 40, glowCoreAlpha);
			buffer.vertex(model, outerX1, 0.01f, outerZ1).color(255, 40, 30, 0);
			buffer.vertex(model, outerX2, 0.01f, outerZ2).color(255, 40, 30, 0);
			buffer.vertex(model, 0f, 0.01f, 0f).color(255, 60, 40, glowCoreAlpha);
		}

		// Сам нимб — объёмное красное кольцо (тор), идеально круглое сверху.
		for (int i = 0; i < SEGMENTS; i++) {
			double theta1 = 2 * Math.PI * i / SEGMENTS;
			double theta2 = 2 * Math.PI * (i + 1) / SEGMENTS;

			for (int j = 0; j < TUBE_SEGMENTS; j++) {
				double phi1 = 2 * Math.PI * j / TUBE_SEGMENTS;
				double phi2 = 2 * Math.PI * (j + 1) / TUBE_SEGMENTS;

				addTorusVertex(buffer, model, theta1, phi1, glowAlpha);
				addTorusVertex(buffer, model, theta2, phi1, glowAlpha);
				addTorusVertex(buffer, model, theta2, phi2, glowAlpha);
				addTorusVertex(buffer, model, theta1, phi2, glowAlpha);
			}
		}
	}

	private static void addTorusVertex(VertexConsumer buffer, Matrix4f model, double theta, double phi, int alpha) {
		float cosPhi = (float) Math.cos(phi);
		float sinPhi = (float) Math.sin(phi);
		float cosTheta = (float) Math.cos(theta);
		float sinTheta = (float) Math.sin(theta);

		float x = (RING_RADIUS + TUBE_RADIUS * cosPhi) * cosTheta;
		float z = (RING_RADIUS + TUBE_RADIUS * cosPhi) * sinTheta;
		float y = TUBE_RADIUS * sinPhi;

		// Верх кольца светится ярче (имитация мягкого света), низ - глубокий красный.
		float light = (sinPhi + 1f) / 2f;
		int r = 255;
		int g = (int) (35 + 90 * light);
		int b = (int) (25 + 60 * light);

		buffer.vertex(model, x, y, z).color(r, g, b, alpha);
	}
}
