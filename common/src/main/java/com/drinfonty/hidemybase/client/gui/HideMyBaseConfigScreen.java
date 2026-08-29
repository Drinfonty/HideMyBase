package com.drinfonty.hidemybase.client.gui;

import com.drinfonty.hidemybase.HideMyBase;
import com.drinfonty.hidemybase.client.HideMyBaseClient;
import com.drinfonty.hidemybase.config.ClientConfig;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen, reached from ModMenu on Fabric and the Config button on NeoForge.
 *
 * <p>It exists because a JSON file is a bad interface for the one thing a player actually needs to
 * do here - roll a new secret - and most players will never find {@code config/hidemybase.json}.
 *
 * <p>Built entirely from widgets, with no {@code render} override and no direct drawing. That is
 * deliberate: 26.x replaced {@code render(GuiGraphics, ...)} with
 * {@code extractRenderState(GuiGraphicsExtractor, ...)}, so any screen that paints its own title or
 * background has to be forked per branch, which is what RedFX ended up doing. Letting the widgets
 * draw themselves keeps this one file shared across all six version branches; the only thing that
 * genuinely varies is returning to the parent screen, which lives in {@link ScreenCompat}.
 *
 * <p>Every change is applied and saved immediately - there is no OK/Cancel - because the effect is
 * visible behind the screen and a player should be able to see what a setting does.
 */
public class HideMyBaseConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int WIDGET_WIDTH = 260;
	private static final int BUTTON_WIDTH = 128;

	/** Long enough to show the secret is 32 hex characters, short enough not to hint at any of it. */
	private static final String MASK = "•".repeat(32);

	private final Screen parent;

	private boolean revealed;

	public HideMyBaseConfigScreen(Screen parent) {
		super(Component.literal(HideMyBase.MOD_NAME));
		this.parent = parent;
	}

	private ClientConfig config() {
		return HideMyBaseClient.config();
	}

	@Override
	protected void init() {
		ClientConfig config = config();
		int centre = this.width / 2;
		int left = centre - WIDGET_WIDTH / 2;
		int y = Math.max(30, this.height / 2 - 100);

		addCentredLabel(this.title, centre, y);
		y += ROW_HEIGHT + 6;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.enabled)
			.create(left, y, WIDGET_WIDTH, 20, Component.literal("Protection"),
				(button, value) -> {
					config().enabled = value;
					HideMyBaseClient.applyAndSave();
				}));
		y += ROW_HEIGHT;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.scrambleRotation)
			.create(left, y, WIDGET_WIDTH, 20, Component.literal("Scramble block rotations"),
				(button, value) -> {
					config().scrambleRotation = value;
					HideMyBaseClient.applyAndSave();
				}));
		y += ROW_HEIGHT;

		CycleButton<Boolean> offset = CycleButton.onOffBuilder(config.scrambleOffset)
			.create(left, y, WIDGET_WIDTH, 20, Component.literal("Scramble plant positions"),
				(button, value) -> {
					config().scrambleOffset = value;
					HideMyBaseClient.applyAndSave();
				});
		offset.setTooltip(Tooltip.create(Component.literal(
			"Also scrambles the sideways nudge on grass, flowers, bamboo and dripstone.\n\n"
				+ "Closes a second, smaller leak. Off by default because Minecraft uses the same "
				+ "value for bamboo and dripstone collision boxes, and only your client is "
				+ "scrambled - so those will look slightly offset from where you bump into them.")));
		this.addRenderableWidget(offset);
		y += ROW_HEIGHT;

		CycleButton<Boolean> perWorld = CycleButton.onOffBuilder(config.perWorldSalt)
			.create(left, y, WIDGET_WIDTH, 20, Component.literal("Separate secret per world"),
				(button, value) -> {
					config().perWorldSalt = value;
					HideMyBaseClient.applyAndSave();
				});
		perWorld.setTooltip(Tooltip.create(Component.literal(
			"Gives every world and server its own scramble, so a screenshot of one base reveals "
				+ "nothing about another.")));
		this.addRenderableWidget(perWorld);
		y += ROW_HEIGHT + 10;

		addCentredLabel(secretText(), centre, y);
		y += ROW_HEIGHT;

		this.addRenderableWidget(Button.builder(revealText(), button -> {
				this.revealed = !this.revealed;
				this.rebuildWidgets();
			})
			.bounds(left, y, BUTTON_WIDTH, 20)
			.tooltip(Tooltip.create(Component.literal(
				"Hidden by default on purpose. Anyone who sees your secret can undo the scramble "
					+ "for every screenshot you have ever posted, so do not reveal it on stream.")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("New secret"), button -> {
				config().regenerateSecret();
				HideMyBaseClient.applyAndSave();
				this.rebuildWidgets();
			})
			.bounds(left + WIDGET_WIDTH - BUTTON_WIDTH, y, BUTTON_WIDTH, 20)
			.tooltip(Tooltip.create(Component.literal(
				"Rolls a new random secret and re-scrambles the world immediately.\n\n"
					+ "Do this if you think your secret has been seen. Screenshots you posted "
					+ "before now keep the old scramble - this protects what you post next.")))
			.build());
		y += ROW_HEIGHT + 10;

		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
			.bounds(centre - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, 20)
			.build());
	}

	/**
	 * A label exactly as wide as its own text, positioned so that text is centred.
	 *
	 * <p>Neither {@code alignCenter()} nor a fixed-width widget works across the range: 26.2
	 * dropped the alignment methods, and {@code StringWidget}'s default alignment is not the same
	 * on both ends - a full-width label came out left-aligned on 26.2 and centred on 1.21.1. Sizing
	 * the widget to the string sidesteps alignment altogether.
	 */
	private void addCentredLabel(Component text, int centre, int y) {
		int width = this.font.width(text);
		this.addRenderableWidget(new StringWidget(centre - width / 2, y, width, 20, text, this.font));
	}

	private Component secretText() {
		String secret = config().secret;

		if (secret == null || secret.isEmpty()) {
			return Component.literal("Secret: (not generated yet)");
		}

		return Component.literal("Secret: " + (this.revealed ? secret : MASK));
	}

	private Component revealText() {
		return Component.literal(this.revealed ? "Hide secret" : "Reveal secret");
	}

	@Override
	public void onClose() {
		HideMyBaseClient.applyAndSave();
		ScreenCompat.setScreen(this.minecraft, this.parent);
	}
}
