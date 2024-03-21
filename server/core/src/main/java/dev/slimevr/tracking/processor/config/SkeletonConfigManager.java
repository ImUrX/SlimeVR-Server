package dev.slimevr.tracking.processor.config;

import dev.slimevr.VRServer;
import dev.slimevr.autobone.errors.BodyProportionError;
import dev.slimevr.autobone.errors.proportions.ProportionLimiter;
import dev.slimevr.config.ConfigManager;
import dev.slimevr.config.SkeletonConfig;
import dev.slimevr.tracking.processor.BoneType;
import dev.slimevr.tracking.processor.HumanPoseManager;
import io.github.axisangles.ktmath.Vector3;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;


public class SkeletonConfigManager {

	public static final SkeletonConfigOffsets[] HEIGHT_OFFSETS = new SkeletonConfigOffsets[] {
		SkeletonConfigOffsets.NECK,
		SkeletonConfigOffsets.UPPER_CHEST,
		SkeletonConfigOffsets.CHEST,
		SkeletonConfigOffsets.WAIST,
		SkeletonConfigOffsets.HIP,
		SkeletonConfigOffsets.UPPER_LEG,
		SkeletonConfigOffsets.LOWER_LEG
	};

	protected final EnumMap<SkeletonConfigOffsets, Float> configOffsets = new EnumMap<>(
		SkeletonConfigOffsets.class
	);
	protected final EnumMap<SkeletonConfigToggles, Boolean> configToggles = new EnumMap<>(
		SkeletonConfigToggles.class
	);
	protected final EnumMap<SkeletonConfigValues, Float> configValues = new EnumMap<>(
		SkeletonConfigValues.class
	);

	protected boolean[] changedToggles = new boolean[SkeletonConfigToggles.values.length];
	protected boolean[] changedValues = new boolean[SkeletonConfigValues.values.length];

	protected final EnumMap<BoneType, Vector3> nodeOffsets = new EnumMap<>(
		BoneType.class
	);

	protected final boolean autoUpdateOffsets;
	protected HumanPoseManager humanPoseManager;
	protected float offsetHeight = calculateUserHeight();
	private final SkeletonConfig config = VRServer.Companion.getInstance().configManager
		.getVrConfig()
		.getSkeleton();
	static final float FLOOR_OFFSET = 0.05f;

	public SkeletonConfigManager(boolean autoUpdateOffsets) {
		this.autoUpdateOffsets = autoUpdateOffsets;

		updateSettingsInSkeleton();

		if (autoUpdateOffsets) {
			computeAllNodeOffsets();
		}
	}

	public SkeletonConfigManager(
		boolean autoUpdateOffsets,
		HumanPoseManager humanPoseManager
	) {
		this.autoUpdateOffsets = autoUpdateOffsets;
		this.humanPoseManager = humanPoseManager;

		updateSettingsInSkeleton();

		if (autoUpdateOffsets) {
			computeAllNodeOffsets();
		}
	}

	public void updateSettingsInSkeleton() {
		if (humanPoseManager == null)
			return;

		for (SkeletonConfigToggles config : SkeletonConfigToggles.values) {
			Boolean val = configToggles.get(config);
			humanPoseManager.updateToggleState(config, val == null ? config.defaultValue : val);
		}

		for (SkeletonConfigValues config : SkeletonConfigValues.values) {
			Float val = configValues.get(config);
			humanPoseManager.updateValueState(config, val == null ? config.defaultValue : val);
		}
	}

	public void updateNodeOffsetsInSkeleton() {
		if (humanPoseManager == null)
			return;

		for (BoneType config : BoneType.values) {
			Vector3 val = nodeOffsets.get(config);
			if (val != null)
				humanPoseManager.updateNodeOffset(config, val);
		}
	}

	public void setOffset(
		SkeletonConfigOffsets config,
		Float newValue,
		boolean computeOffsets
	) {
		if (newValue != null) {
			configOffsets.put(config, newValue);
		} else {
			configOffsets.remove(config);
		}

		// Re-compute the affected offsets
		if (computeOffsets && autoUpdateOffsets && config.affectedOffsets != null) {
			for (BoneType offset : config.affectedOffsets) {
				computeNodeOffset(offset);
			}
		}

		// Re-calculate user height
		offsetHeight = calculateUserHeight();
	}

	public void setOffset(SkeletonConfigOffsets config, Float newValue) {
		setOffset(config, newValue, true);
	}

	public float getOffset(SkeletonConfigOffsets config) {
		if (config == null) {
			return 0f;
		}

		Float val = configOffsets.get(config);
		return val != null ? val : config.defaultValue;
	}

	private float calculateUserHeight() {
		float height = 0f;
		for (SkeletonConfigOffsets offset : HEIGHT_OFFSETS) {
			height += getOffset(offset);
		}
		return height;
	}

	public void setUserHeight(Float value) {
		config.height = value;
	}

	public Float getConfigUserHeight() {
		return config.height;
	}

	public float getUserHeight() {
		return config.height == null ? offsetHeight : config.height;
	}

	public void setToggle(SkeletonConfigToggles config, Boolean newValue) {
		if (newValue != null) {
			if (configToggles.get(config) != null && (newValue != configToggles.get(config))) {
				changedToggles[config.id - 1] = true;
			}
			configToggles.put(config, newValue);
		} else {
			configToggles.remove(config);
		}

		// Updates in skeleton
		if (humanPoseManager != null) {
			humanPoseManager
				.updateToggleState(config, newValue != null ? newValue : config.defaultValue);
		}
	}

	public boolean getToggle(SkeletonConfigToggles config) {
		if (config == null) {
			return false;
		}

		Boolean val = configToggles.get(config);
		return val != null ? val : config.defaultValue;
	}

	public void setValue(SkeletonConfigValues config, Float newValue) {
		if (newValue != null) {
			if (configValues.get(config) != null && (!newValue.equals(configValues.get(config)))) {
				changedValues[config.id - 1] = true;
			}
			configValues.put(config, newValue);
		} else {
			configValues.remove(config);
		}

		// Updates in skeleton
		if (humanPoseManager != null) {
			humanPoseManager
				.updateValueState(config, newValue != null ? newValue : config.defaultValue);
		}
	}

	public float getValue(SkeletonConfigValues config) {
		if (config == null) {
			return 0f;
		}

		Float val = configValues.get(config);
		return val != null ? val : config.defaultValue;
	}

	protected void setNodeOffset(BoneType nodeOffset, float x, float y, float z) {
		Vector3 offset = nodeOffsets.get(nodeOffset);

		if (offset == null) {
			offset = new Vector3(x, y, z);
			nodeOffsets.put(nodeOffset, offset);
		} else {
			offset = new Vector3(x, y, z);
		}

		// Updates in skeleton
		if (humanPoseManager != null) {
			humanPoseManager.updateNodeOffset(nodeOffset, offset);
		}
	}

	public void computeNodeOffset(BoneType nodeOffset) {
		switch (nodeOffset) {
			case HEAD -> setNodeOffset(nodeOffset, 0, 0, getOffset(SkeletonConfigOffsets.HEAD));
			case NECK -> setNodeOffset(nodeOffset, 0, -getOffset(SkeletonConfigOffsets.NECK), 0);
			case UPPER_CHEST -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.UPPER_CHEST),
				0
			);
			case CHEST_TRACKER -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.CHEST_OFFSET)
					- getOffset(SkeletonConfigOffsets.CHEST),
				-getOffset(SkeletonConfigOffsets.SKELETON_OFFSET)
			);
			case CHEST -> setNodeOffset(nodeOffset, 0, -getOffset(SkeletonConfigOffsets.CHEST), 0);
			case WAIST -> setNodeOffset(nodeOffset, 0, -getOffset(SkeletonConfigOffsets.WAIST), 0);
			case HIP -> setNodeOffset(nodeOffset, 0, -getOffset(SkeletonConfigOffsets.HIP), 0);
			case HIP_TRACKER -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.HIP_OFFSET),
				-getOffset(SkeletonConfigOffsets.SKELETON_OFFSET)
			);
			case LEFT_HIP -> setNodeOffset(
				nodeOffset,
				-getOffset(SkeletonConfigOffsets.HIPS_WIDTH) / 2f,
				0,
				0
			);
			case RIGHT_HIP -> setNodeOffset(
				nodeOffset,
				getOffset(SkeletonConfigOffsets.HIPS_WIDTH) / 2f,
				0,
				0
			);
			case LEFT_UPPER_LEG, RIGHT_UPPER_LEG -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.UPPER_LEG),
				0
			);
			case LEFT_KNEE_TRACKER, RIGHT_KNEE_TRACKER -> setNodeOffset(
				nodeOffset,
				0,
				0,
				-getOffset(SkeletonConfigOffsets.SKELETON_OFFSET)
			);
			case LEFT_LOWER_LEG, RIGHT_LOWER_LEG -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.LOWER_LEG),
				-getOffset(SkeletonConfigOffsets.FOOT_SHIFT)
			);
			case LEFT_FOOT, RIGHT_FOOT -> setNodeOffset(
				nodeOffset,
				0,
				0,
				-getOffset(SkeletonConfigOffsets.FOOT_LENGTH)
			);
			case LEFT_FOOT_TRACKER, RIGHT_FOOT_TRACKER -> setNodeOffset(
				nodeOffset,
				0,
				0,
				-getOffset(SkeletonConfigOffsets.SKELETON_OFFSET)
			);
			case LEFT_SHOULDER -> setNodeOffset(
				nodeOffset,
				-getOffset(SkeletonConfigOffsets.SHOULDERS_WIDTH) / 2f,
				-getOffset(SkeletonConfigOffsets.SHOULDERS_DISTANCE),
				0
			);
			case RIGHT_SHOULDER -> setNodeOffset(
				nodeOffset,
				getOffset(SkeletonConfigOffsets.SHOULDERS_WIDTH) / 2f,
				-getOffset(SkeletonConfigOffsets.SHOULDERS_DISTANCE),
				0
			);
			case LEFT_UPPER_ARM, RIGHT_UPPER_ARM -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.UPPER_ARM),
				0
			);
			case LEFT_LOWER_ARM, RIGHT_LOWER_ARM -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.LOWER_ARM),
				0
			);
			case LEFT_HAND, RIGHT_HAND -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.HAND_Y),
				-getOffset(SkeletonConfigOffsets.HAND_Z)
			);
			case LEFT_ELBOW_TRACKER, RIGHT_ELBOW_TRACKER -> setNodeOffset(
				nodeOffset,
				0,
				-getOffset(SkeletonConfigOffsets.ELBOW_OFFSET),
				0
			);
		}
	}

	public void computeAllNodeOffsets() {
		for (BoneType offset : BoneType.values) {
			computeNodeOffset(offset);
		}
	}

	public void setOffsets(
		Map<SkeletonConfigOffsets, Float> configOffsets,
		boolean computeOffsets
	) {
		if (configOffsets != null) {
			configOffsets.forEach((key, value) -> {
				// Do not recalculate the offsets, these are done in bulk at the
				// end
				setOffset(key, value, false);
			});
		}

		if (computeOffsets && autoUpdateOffsets) {
			computeAllNodeOffsets();
		}
	}

	public void setOffsets(
		Map<SkeletonConfigOffsets, Float> configOffsets
	) {
		setOffsets(configOffsets, true);
	}

	public void setOffsets(SkeletonConfigManager skeletonConfigManager) {
		// Don't recalculate node offsets, just re-use them from skeletonConfig
		setOffsets(
			skeletonConfigManager.configOffsets,
			false
		);

		// Copy skeletonConfig's nodeOffsets as the configs are all the same
		skeletonConfigManager.nodeOffsets.forEach((key, value) -> {
			setNodeOffset(key, value.getX(), value.getY(), value.getZ());
		});
	}

	public void resetOffsets() {
		if (humanPoseManager != null) {
			for (SkeletonConfigOffsets config : SkeletonConfigOffsets.values) {
				resetOffset(config);
			}
		} else {
			configOffsets.clear();
			if (autoUpdateOffsets) {
				computeAllNodeOffsets();
			}
		}
	}


	public void resetToggles() {
		configToggles.clear();

		// Updates in skeleton
		if (humanPoseManager != null) {
			for (SkeletonConfigToggles config : SkeletonConfigToggles.values) {
				humanPoseManager.updateToggleState(config, config.defaultValue);
			}
		}

		// Remove from config to use default if they change in the future.
		Arrays.fill(changedToggles, false);
		for (SkeletonConfigToggles value : SkeletonConfigToggles.values) {
			config
				.getToggles()
				.remove(value.configKey);
			// Set default in skeleton
			setToggle(value, value.defaultValue);
		}
	}

	public void resetValues() {
		configValues.clear();

		// Updates in skeleton
		if (humanPoseManager != null) {
			for (SkeletonConfigValues config : SkeletonConfigValues.values) {
				humanPoseManager.updateValueState(config, config.defaultValue);
			}
		}

		// Remove from config to use default if they change in the future.
		Arrays.fill(changedValues, false);
		for (SkeletonConfigValues value : SkeletonConfigValues.values) {
			VRServer.Companion.getInstance().configManager
				.getVrConfig()
				.getSkeleton()
				.getValues()
				.remove(value.configKey);
			// Set default in skeleton
			setValue(value, value.defaultValue);
		}
	}

	public void resetAllConfigs() {
		resetOffsets();
		resetToggles();
		resetValues();
	}

	public void resetOffset(SkeletonConfigOffsets config) {
		if (config == null) {
			return;
		}

		switch (config) {
			case UPPER_CHEST, CHEST, WAIST, HIP, UPPER_LEG, LOWER_LEG -> {
				float height = getConfigUserHeight() != null
					? getConfigUserHeight()
					: humanPoseManager.getHmdHeight()
						/ BodyProportionError.eyeHeightToHeightRatio;
				if (height > 0.5f) { // Reset only if floor level seems right,
					ProportionLimiter proportionLimiter = BodyProportionError
						.getProportionLimitMap()
						.get(config);
					if (proportionLimiter != null) {
						setOffset(
							config,
							height * proportionLimiter.getTargetRatio()
						);
					} else {
						setOffset(config, null);
					}
				} else { // if floor level is incorrect
					setOffset(config, null);
				}
			}
			default -> setOffset(config, null);
		}
	}

	public void loadFromConfig(ConfigManager configManager) {
		SkeletonConfig skeletonConfig = configManager.getVrConfig().getSkeleton();

		// Load offsets
		Map<String, Float> offsets = skeletonConfig.getOffsets();
		for (SkeletonConfigOffsets configValue : SkeletonConfigOffsets.values) {
			Float val = offsets.get(configValue.configKey);
			if (val != null) {
				// Do not recalculate the offsets, these are done in bulk at the
				// end
				setOffset(configValue, val, false);
			}
		}

		// Load toggles
		Map<String, Boolean> toggles = skeletonConfig.getToggles();
		for (SkeletonConfigToggles configValue : SkeletonConfigToggles.values) {
			Boolean val = toggles.get(configValue.configKey);
			if (val != null) {
				setToggle(configValue, val);
			} else if (humanPoseManager != null) {
				humanPoseManager.updateToggleState(configValue, configValue.defaultValue);
			}
		}

		// Load values
		Map<String, Float> values = skeletonConfig.getValues();
		for (SkeletonConfigValues configValue : SkeletonConfigValues.values) {
			Float val = values.get(configValue.configKey);
			if (val != null) {
				setValue(configValue, val);
			} else if (humanPoseManager != null) {
				humanPoseManager.updateValueState(configValue, configValue.defaultValue);
			}
		}

		// Updates all offsets
		if (autoUpdateOffsets) {
			computeAllNodeOffsets();
		}
	}

	public void save() {

		// Write all possible values to keep consistent even if defaults changed
		for (SkeletonConfigOffsets value : SkeletonConfigOffsets.values) {
			config.getOffsets().put(value.configKey, getOffset(value));
		}

		// Only write changed values to keep using defaults if not changed
		for (SkeletonConfigToggles value : SkeletonConfigToggles.values) {
			if (changedToggles[value.id - 1])
				config.getToggles().put(value.configKey, getToggle(value));
		}

		// Only write changed values to keep using defaults if not changed
		for (SkeletonConfigValues value : SkeletonConfigValues.values) {
			if (changedValues[value.id - 1])
				config.getValues().put(value.configKey, getValue(value));
		}
	}
}
