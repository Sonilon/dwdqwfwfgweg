package com.pirs.holyinvisible.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Рисует объёмный красный нимб (тор) над головой невидимых игроков.
 *
 * Рендер идёт как обычная 3D-геометрия с включённым тестом глубины,
 * поэтому нимб автоматически перекрывается блоками (не виден сквозь стены)
 * и при этом НЕ показывает силуэт/модель самого игрока — только сам нимб.
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

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();
		RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);

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

			drawHalo(matrices, pulse);

			matrices.pop();
		}

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
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

	private static void drawHalo(MatrixStack matrices, float pulse) {
		Tessellator tessellator = Tessellator.getInstance();

		Matrix4f model = matrices.peek().getPositionMatrix();

		int glowAlpha = (int) (170 + 60 * pulse);

		// Мягкое красное свечение под нимбом (диск, тает к краям) - даёт объём и "ауру".
		BufferBuilder glowBuffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		glowBuffer.vertex(model, 0f, 0.01f, 0f).color(255, 60, 40, (int) (90 * pulse));
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = 2 * Math.PI * i / SEGMENTS;
			float px = (float) (Math.cos(angle) * RING_RADIUS * 1.6f);
			float pz = (float) (Math.sin(angle) * RING_RADIUS * 1.6f);
			glowBuffer.vertex(model, px, 0.01f, pz).color(255, 40, 30, 0);
		}
		BufferRenderer.drawWithGlobalProgram(glowBuffer.end());

		// Сам нимб — объёмное красное кольцо (тор), идеально круглое сверху.
		BufferBuilder haloBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < SEGMENTS; i++) {
			double theta1 = 2 * Math.PI * i / SEGMENTS;
			double theta2 = 2 * Math.PI * (i + 1) / SEGMENTS;

			for (int j = 0; j < TUBE_SEGMENTS; j++) {
				double phi1 = 2 * Math.PI * j / TUBE_SEGMENTS;
				double phi2 = 2 * Math.PI * (j + 1) / TUBE_SEGMENTS;

				addTorusVertex(haloBuffer, model, theta1, phi1, glowAlpha);
				addTorusVertex(haloBuffer, model, theta2, phi1, glowAlpha);
				addTorusVertex(haloBuffer, model, theta2, phi2, glowAlpha);
				addTorusVertex(haloBuffer, model, theta1, phi2, glowAlpha);
			}
		}
		BufferRenderer.drawWithGlobalProgram(haloBuffer.end());
	}

	private static void addTorusVertex(BufferBuilder buffer, Matrix4f model, double theta, double phi, int alpha) {
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
