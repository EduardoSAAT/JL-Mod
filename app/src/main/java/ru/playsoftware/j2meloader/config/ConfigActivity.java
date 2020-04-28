/*
 * Copyright 2018 Nikita Shakarun
 * Copyright 2020 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.config;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.microedition.lcdui.pointer.VirtualKeyboard;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.param.SharedPreferencesContainer;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.base.BaseActivity;
import ru.playsoftware.j2meloader.settings.KeyMapperActivity;
import ru.playsoftware.j2meloader.util.FileUtils;
import yuku.ambilwarna.AmbilWarnaDialog;

public class ConfigActivity extends BaseActivity implements View.OnClickListener {

	public static final String ACTION_EDIT = "config.edit";
	public static final String ACTION_EDIT_PROFILE = "config.edit.profile";
	public static final String CONFIG_PATH_KEY = "configPath";
	public static final String MIDLET_NAME_KEY = "midletName";
	private static final int[] SCREEN_SIZES = {128, 176, 220, 320};
	private static final int[] FONT_SIZES = {
			9, 13, 15, // 128
			13, 15, 20, // 176
			15, 18, 22, // 220
			18, 22, 26, // 320
	};

	protected ScrollView rootContainer;
	protected EditText tfScreenWidth;
	protected EditText tfScreenHeight;
	protected EditText tfScreenBack;
	protected SeekBar sbScaleRatio;
	protected EditText tfScaleRatioValue;
	protected Spinner spOrientation;
	protected CheckBox cxScaleToFit;
	protected CheckBox cxKeepAspectRatio;
	protected CheckBox cxFilter;
	protected CheckBox cxImmediate;
	protected CheckBox cxHwAcceleration;
	protected CheckBox cxParallel;
	protected CheckBox cxForceFullscreen;
	protected CheckBox cxShowFps;
	protected CheckBox cxLimitFps;
	protected EditText tfFpsLimit;

	protected EditText tfFontSizeSmall;
	protected EditText tfFontSizeMedium;
	protected EditText tfFontSizeLarge;
	protected CheckBox cxFontSizeInSP;
	protected CheckBox cxShowKeyboard;

	private View vkContainer;
	protected CheckBox cxVKFeedback;
	protected CheckBox cxTouchInput;

	protected Spinner spVKType;
	protected Spinner spLayout;
	private Spinner spButtonsShape;
	protected SeekBar sbVKAlpha;
	protected EditText tfVKHideDelay;
	protected EditText tfVKFore;
	protected EditText tfVKBack;
	protected EditText tfVKSelFore;
	protected EditText tfVKSelBack;
	protected EditText tfVKOutline;

	protected EditText tfSystemProperties;

	protected ArrayList<String> screenPresets = new ArrayList<>();

	protected ArrayList<Integer> fontSmall;
	protected ArrayList<Integer> fontMedium;
	protected ArrayList<Integer> fontLarge;
	protected ArrayList<String> fontAdapter;

	private File keylayoutFile;
	private File dataDir;
	private SharedPreferencesContainer params;
	private FragmentManager fragmentManager;
	private boolean isProfile;
	private Display display;
	private File configDir;
	private String defProfile;

	@SuppressLint({"StringFormatMatches", "StringFormatInvalid"})
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		String action = intent.getAction();
		isProfile = ACTION_EDIT_PROFILE.equals(action);
		boolean showSettings = isProfile || ACTION_EDIT.equals(action);
		String dirName = intent.getDataString();
		if (dirName == null) {
			finish();
			return;
		}
		if (isProfile) {
			setResult(RESULT_OK, new Intent().setData(intent.getData()));
			configDir = new File(Config.PROFILES_DIR, dirName);
			setTitle(dirName);
		} else {
			setTitle(intent.getStringExtra(MIDLET_NAME_KEY));
			dataDir = new File(Config.DATA_DIR, dirName);
			dataDir.mkdirs();
			configDir = new File(Config.CONFIGS_DIR, dirName);
		}
		configDir.mkdirs();

		params = new SharedPreferencesContainer(configDir);
		boolean loaded = params.load();
		if (params.getInt("version", 0) < 1) {
			updateProperties();
			params.edit().putInt("version", 1).apply();
		}

		if (loaded && !showSettings) {
			startMIDlet();
			return;
		}
		final String defName = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
				.getString(Config.DEFAULT_PROFILE_KEY, null);
		if (defName != null) {
			defProfile = Config.PROFILES_DIR + '/' + defName;
			FileUtils.copyFiles(defProfile, configDir.getAbsolutePath(), null);
		}
		loadKeyLayout();
		setContentView(R.layout.activity_config);
		//noinspection ConstantConditions
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		display = getWindowManager().getDefaultDisplay();
		fragmentManager = getSupportFragmentManager();

		rootContainer = findViewById(R.id.configRoot);
		tfScreenWidth = findViewById(R.id.tfScreenWidth);
		tfScreenHeight = findViewById(R.id.tfScreenHeight);
		tfScreenBack = findViewById(R.id.tfScreenBack);
		cxScaleToFit = findViewById(R.id.cxScaleToFit);
		sbScaleRatio = findViewById(R.id.sbScaleRatio);
		tfScaleRatioValue = findViewById(R.id.tfScaleRatioValue);
		spOrientation = findViewById(R.id.spOrientation);
		cxKeepAspectRatio = findViewById(R.id.cxKeepAspectRatio);
		cxFilter = findViewById(R.id.cxFilter);
		cxImmediate = findViewById(R.id.cxImmediate);
		cxHwAcceleration = findViewById(R.id.cxHwAcceleration);
		cxParallel = findViewById(R.id.cxParallel);
		cxForceFullscreen = findViewById(R.id.cxForceFullscreen);
		cxShowFps = findViewById(R.id.cxShowFps);
		cxLimitFps = findViewById(R.id.cxLimitFps);
		tfFpsLimit = findViewById(R.id.tfFpsLimit);

		tfFontSizeSmall = findViewById(R.id.tfFontSizeSmall);
		tfFontSizeMedium = findViewById(R.id.tfFontSizeMedium);
		tfFontSizeLarge = findViewById(R.id.tfFontSizeLarge);
		cxFontSizeInSP = findViewById(R.id.cxFontSizeInSP);
		tfSystemProperties = findViewById(R.id.tfSystemProperties);

		cxTouchInput = findViewById(R.id.cxTouchInput);
		cxShowKeyboard = findViewById(R.id.cxIsShowKeyboard);
		vkContainer = findViewById(R.id.configVkContainer);
		cxVKFeedback = findViewById(R.id.cxVKFeedback);
		cxTouchInput = findViewById(R.id.cxTouchInput);

		spVKType = findViewById(R.id.spVKType);
		spLayout = findViewById(R.id.spLayout);
		spButtonsShape = findViewById(R.id.spButtonsShape);
		sbVKAlpha = findViewById(R.id.sbVKAlpha);
		tfVKHideDelay = findViewById(R.id.tfVKHideDelay);
		tfVKFore = findViewById(R.id.tfVKFore);
		tfVKBack = findViewById(R.id.tfVKBack);
		tfVKSelFore = findViewById(R.id.tfVKSelFore);
		tfVKSelBack = findViewById(R.id.tfVKSelBack);
		tfVKOutline = findViewById(R.id.tfVKOutline);

		fillScreenSizePresets(display.getWidth(), display.getHeight());

		fontSmall = new ArrayList<>();
		fontMedium = new ArrayList<>();
		fontLarge = new ArrayList<>();
		fontAdapter = new ArrayList<>();

		addFontSizePreset("128 x 128", 9, 13, 15);
		addFontSizePreset("128 x 160", 13, 15, 20);
		addFontSizePreset("176 x 220", 15, 18, 22);
		addFontSizePreset("240 x 320", 18, 22, 26);

		findViewById(R.id.cmdScreenSizePresets).setOnClickListener(this::showScreenPresets);
		findViewById(R.id.cmdSwapSizes).setOnClickListener(this);
		findViewById(R.id.cmdAddToPreset).setOnClickListener(v -> addResolutionToPresets());
		findViewById(R.id.cmdFontSizePresets).setOnClickListener(this);
		findViewById(R.id.cmdScreenBack).setOnClickListener(this);
		findViewById(R.id.cmdVKBack).setOnClickListener(this);
		findViewById(R.id.cmdVKFore).setOnClickListener(this);
		findViewById(R.id.cmdVKSelBack).setOnClickListener(this);
		findViewById(R.id.cmdVKSelFore).setOnClickListener(this);
		findViewById(R.id.cmdVKOutline).setOnClickListener(this);
		findViewById(R.id.btEncoding).setOnClickListener(this::showCharsetPicker);
		sbScaleRatio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) tfScaleRatioValue.setText(String.valueOf(progress));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		tfScaleRatioValue.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				sbScaleRatio.setProgress(Integer.parseInt(s.toString()));
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			cxHwAcceleration.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if (isChecked) {
					cxParallel.setChecked(false);
				}
			});
			cxParallel.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if (isChecked) {
					cxHwAcceleration.setChecked(false);
				}
			});
		}
		vkContainer.setVisibility(cxShowKeyboard.isChecked() ? View.VISIBLE : View.GONE);
		cxShowKeyboard.setOnCheckedChangeListener((b, checked) -> {
			if (checked) {
				vkContainer.setVisibility(View.VISIBLE);
			} else {
				vkContainer.setVisibility(View.GONE);
			}
			View.OnLayoutChangeListener onLayoutChangeListener = new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
					v.scrollTo(0, ConfigActivity.this.findViewById(R.id.tvKeyboardHeader).getBottom());
					v.removeOnLayoutChangeListener(this);
				}
			};
			rootContainer.addOnLayoutChangeListener(onLayoutChangeListener);
		});
	}

	private void updateProperties() {
		String[] defaults = getDefaultProperties().split("\\n");
		String properties = params.getString("SystemProperties", "");
		StringBuilder sb = new StringBuilder(properties);
		for (String line : defaults) {
			if (properties.contains(line.substring(0, line.indexOf(':')))) continue;
			sb.append(line).append('\n');
		}
		params.putString("SystemProperties", sb.toString()).apply();
	}

	private void showCharsetPicker(View v) {
		String[] charsets = Charset.availableCharsets().keySet().toArray(new String[0]);
		new AlertDialog.Builder(this).setItems(charsets, (d, w) -> {
			String enc = "microedition.encoding: " + charsets[w];
			String[] props = tfSystemProperties.getText().toString().split("\n");
			int propsLength = props.length;
			if (propsLength == 0) {
				tfSystemProperties.setText(enc);
				return;
			}
			int i = propsLength - 1;
			while (i >= 0) {
				if (props[i].startsWith("microedition.encoding")) {
					props[i] = enc;
					break;
				}
				i--;
			}
			if (i < 0) {
				tfSystemProperties.append(enc);
				return;
			}
			tfSystemProperties.setText(TextUtils.join("\n", props));
		}).setTitle(R.string.pref_encoding_title).show();
	}

	private void loadKeyLayout() {
		File file = new File(configDir, Config.MIDLET_KEY_LAYOUT_FILE);
		keylayoutFile = file;
		if (isProfile || file.exists()) {
			return;
		}
		if (defProfile == null) {
			return;
		}
		File defaultKeyLayoutFile = new File(defProfile, Config.MIDLET_KEY_LAYOUT_FILE);
		if (!defaultKeyLayoutFile.exists()) {
			return;
		}
		try {
			FileUtils.copyFileUsingChannel(defaultKeyLayoutFile, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onPause() {
		saveParams();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadParams();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		fillScreenSizePresets(display.getWidth(), display.getHeight());
	}

	private void fillScreenSizePresets(int w, int h) {
		ArrayList<String> screenPresets = this.screenPresets;
		screenPresets.clear();

		screenPresets.add("128 x 128");
		screenPresets.add("128 x 160");
		screenPresets.add("132 x 176");
		screenPresets.add("176 x 220");
		screenPresets.add("240 x 320");
		screenPresets.add("352 x 416");
		screenPresets.add("640 x 360");
		screenPresets.add("800 x 480");

		if (w > h) {
			screenPresets.add(h * 3 / 4 + " x " + h);
			screenPresets.add(h * 4 / 3 + " x " + h);
		} else {
			screenPresets.add(w + " x " + w * 4 / 3);
			screenPresets.add(w + " x " + w * 3 / 4);
		}

		screenPresets.add(w + " x " + h);
		Set<String> preset = PreferenceManager.getDefaultSharedPreferences(this)
				.getStringSet("ResolutionsPreset", null);
		if (preset != null) {
			screenPresets.addAll(preset);
		}
		Collections.sort(screenPresets, (o1, o2) -> {
			int sep1 = o1.indexOf(" x ");
			int sep2 = o2.indexOf(" x ");
			if (sep1 == -1) {
				if (sep2 != -1) return -1;
				else return 0;
			} else if (sep2 == -1) return 1;
			int r = Integer.decode(o1.substring(0, sep1)).compareTo(Integer.decode(o2.substring(0, sep2)));
			if (r != 0) return r;
			return Integer.decode(o1.substring(sep1 + 3)).compareTo(Integer.decode(o2.substring(sep2 + 3)));
		});
		String prev = null;
		for (Iterator<String> iterator = screenPresets.iterator(); iterator.hasNext(); ) {
			String next = iterator.next();
			if (next.equals(prev)) iterator.remove();
			else prev = next;
		}
	}

	private void addFontSizePreset(String title, int small, int medium, int large) {
		fontSmall.add(small);
		fontMedium.add(medium);
		fontLarge.add(large);
		fontAdapter.add(title);
	}

	@SuppressLint("SetTextI18n")
	public void loadParams() {
		int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
		params.load();
		tfScreenWidth.setText(Integer.toString(params.getInt("ScreenWidth", 240)));
		tfScreenHeight.setText(Integer.toString(params.getInt("ScreenHeight", 320)));
		int color = params.getInt("ScreenBackgroundColor", 0xD0D0D0);
		tfScreenBack.setText(String.format("%06X", color));
		ColorDrawable colorDrawable = new ColorDrawable(color | 0xff000000);
		colorDrawable.setBounds(0,0, size, size);
		tfScreenBack.setCompoundDrawables(null, null, colorDrawable, null);
		sbScaleRatio.setProgress(params.getInt("ScreenScaleRatio", 100));
		tfScaleRatioValue.setText(String.valueOf(sbScaleRatio.getProgress()));
		spOrientation.setSelection(params.getInt("Orientation", 0));
		cxScaleToFit.setChecked(params.getBoolean("ScreenScaleToFit", true));
		cxKeepAspectRatio.setChecked(params.getBoolean("ScreenKeepAspectRatio", true));
		cxFilter.setChecked(params.getBoolean("ScreenFilter", false));
		cxImmediate.setChecked(params.getBoolean("ImmediateMode", false));
		cxParallel.setChecked(params.getBoolean("ParallelRedrawScreen", false));
		cxForceFullscreen.setChecked(params.getBoolean("ForceFullscreen", false));
		cxHwAcceleration.setChecked(params.getBoolean("HwAcceleration", false));
		cxShowFps.setChecked(params.getBoolean("ShowFps", false));
		cxLimitFps.setChecked(params.getBoolean("LimitFps", false));

		tfFontSizeSmall.setText(Integer.toString(params.getInt("FontSizeSmall", 18)));
		tfFontSizeMedium.setText(Integer.toString(params.getInt("FontSizeMedium", 22)));
		tfFontSizeLarge.setText(Integer.toString(params.getInt("FontSizeLarge", 26)));
		cxFontSizeInSP.setChecked(params.getBoolean("FontApplyDimensions", false));
		cxShowKeyboard.setChecked(params.getBoolean(("ShowKeyboard"), true));
		cxVKFeedback.setChecked(params.getBoolean(("VirtualKeyboardFeedback"), false));
		cxVKFeedback.setEnabled(cxShowKeyboard.isChecked());
		cxTouchInput.setChecked(params.getBoolean(("TouchInput"), true));
		tfFpsLimit.setText(Integer.toString(params.getInt("FpsLimit", 0)));

		spVKType.setSelection(params.getInt("VirtualKeyboardType", 0));
		spLayout.setSelection(params.getInt("Layout", 0));
		spButtonsShape.setSelection(params.getInt("ButtonShape", VirtualKeyboard.OVAL_SHAPE));
		sbVKAlpha.setProgress(params.getInt("VirtualKeyboardAlpha", 64));
		tfVKHideDelay.setText(Integer.toString(params.getInt("VirtualKeyboardDelay", -1)));

		color = params.getInt("VirtualKeyboardColorBackground", 0xD0D0D0);
		colorDrawable = new ColorDrawable(color | 0xff000000);
		colorDrawable.setBounds(0,0, size, size);
		tfVKBack.setCompoundDrawables(null, null, colorDrawable, null);
		tfVKBack.setText(String.format("%06X", color));

		color = params.getInt("VirtualKeyboardColorForeground", 0x000080);
		colorDrawable = new ColorDrawable(color | 0xff000000);
		colorDrawable.setBounds(0,0, size, size);
		tfVKFore.setCompoundDrawables(null, null, colorDrawable, null);
		tfVKFore.setText(String.format("%06X", color));

		color = params.getInt("VirtualKeyboardColorBackgroundSelected", 0x000080);
		colorDrawable = new ColorDrawable(color | 0xff000000);
		colorDrawable.setBounds(0,0, size, size);
		tfVKSelBack.setCompoundDrawables(null, null, colorDrawable, null);
		tfVKSelBack.setText(String.format("%06X", color));

		color = params.getInt("VirtualKeyboardColorForegroundSelected", 0xFFFFFF);
		colorDrawable = new ColorDrawable(color | 0xff000000);
		colorDrawable.setBounds(0,0, size, size);
		tfVKSelFore.setCompoundDrawables(null, null, colorDrawable, null);
		tfVKSelFore.setText(String.format("%06X", color));

		color = params.getInt("VirtualKeyboardColorOutline", 0xFFFFFF);
		colorDrawable = new ColorDrawable(color | 0xff000000);
		colorDrawable.setBounds(0,0, size, size);
		tfVKOutline.setCompoundDrawables(null, null, colorDrawable, null);
		tfVKOutline.setText(String.format("%06X", color));

		String systemProperties = params.getString("SystemProperties", null);
		if (systemProperties == null) systemProperties = getDefaultProperties();
		tfSystemProperties.setText(systemProperties);
	}

	public static String getDefaultProperties() {
		StringBuilder sb = new StringBuilder();
		sb.append("microedition.sensor.version").append(": ").append("1").append('\n');
		sb.append("microedition.platform").append(": ").append("Nokia 6233").append('\n');
		sb.append("microedition.configuration").append(": ").append("CDLC-1.1").append('\n');
		sb.append("microedition.profiles").append(": ").append("MIDP-2.0").append('\n');
		sb.append("microedition.m3g.version").append(": ").append("1.1").append('\n');
		sb.append("microedition.media.version").append(": ").append("1.0").append('\n');
		sb.append("supports.mixing").append(": ").append("true").append('\n');
		sb.append("supports.audio.capture").append(": ").append("true").append('\n');
		sb.append("supports.video.capture").append(": ").append("false").append('\n');
		sb.append("supports.recording").append(": ").append("false").append('\n');
		sb.append("microedition.pim.version").append(": ").append("1.0").append('\n');
		sb.append("microedition.io.file.FileConnection.version").append(": ").append("1.0").append('\n');
		sb.append("microedition.encoding").append(": ").append("ISO-8859-1").append('\n');
		final String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
		sb.append("user.home").append(": ").append(externalStoragePath).append('\n');
		sb.append("com.siemens.IMEI").append(": ").append("000000000000000").append('\n');
		sb.append("com.siemens.mp.systemfolder.ringingtone").append(": ").append("fs/MyStuff/Ringtones").append('\n');
		sb.append("com.siemens.mp.systemfolder.pictures").append(": ").append("fs/MyStuff/Pictures").append('\n');
		sb.append("com.siemens.OSVersion").append(": ").append("11").append('\n');
		sb.append("device.imei").append(": ").append("000000000000000").append('\n');
		sb.append("com.nokia.mid.impl.isa.visual_radio_operator_id").append(": ").append("0").append('\n');
		sb.append("com.nokia.mid.impl.isa.visual_radio_channel_freq").append(": ").append("0").append('\n');
		return sb.toString();
	}

	private void saveParams() {
		try {
			int width;
			try {
				width = Integer.parseInt(tfScreenWidth.getText().toString());
			} catch (NumberFormatException e) {
				width = 0;
			}
			params.putInt("ScreenWidth", width);
			int height;
			try {
				height = Integer.parseInt(tfScreenHeight.getText().toString());
			} catch (NumberFormatException e) {
				height = 0;
			}
			params.putInt("ScreenHeight", height);
			try {
				params.putInt("ScreenBackgroundColor", Integer.parseInt(tfScreenBack.getText().toString(), 16));
			} catch (NumberFormatException ignored) {}
			params.putInt("ScreenScaleRatio", sbScaleRatio.getProgress());
			params.putInt("Orientation", spOrientation.getSelectedItemPosition());
			params.putBoolean("ScreenScaleToFit", cxScaleToFit.isChecked());
			params.putBoolean("ScreenKeepAspectRatio", cxKeepAspectRatio.isChecked());
			params.putBoolean("ScreenFilter", cxFilter.isChecked());
			params.putBoolean("ImmediateMode", cxImmediate.isChecked());
			params.putBoolean("HwAcceleration", cxHwAcceleration.isChecked());
			params.putBoolean("ParallelRedrawScreen", cxParallel.isChecked());
			params.putBoolean("ForceFullscreen", cxForceFullscreen.isChecked());
			params.putBoolean("ShowFps", cxShowFps.isChecked());
			params.putBoolean("LimitFps", cxLimitFps.isChecked());
			try {
				params.putInt("FpsLimit", Integer.parseInt(tfFpsLimit.getText().toString()));
			} catch (NumberFormatException e) {
				params.putInt("FpsLimit", 0);
			}

			try {
				int value = Integer.parseInt(tfFontSizeSmall.getText().toString());
				params.putInt("FontSizeSmall", value);
			} catch (NumberFormatException e) {
				params.putInt("FontSizeSmall", getFontSizeForResolution(0, width, height));
			}
			try {
				int value = Integer.parseInt(tfFontSizeMedium.getText().toString());
				params.putInt("FontSizeMedium", value);
			} catch (NumberFormatException e) {
				params.putInt("FontSizeMedium", getFontSizeForResolution(1, width, height));
			}
			try {
				int value = Integer.parseInt(tfFontSizeLarge.getText().toString());
				params.putInt("FontSizeLarge", value);
			} catch (NumberFormatException e) {
				params.putInt("FontSizeLarge", getFontSizeForResolution(2, width, height));
			}
			params.putBoolean("FontApplyDimensions", cxFontSizeInSP.isChecked());
			params.putBoolean("ShowKeyboard", cxShowKeyboard.isChecked());
			params.putBoolean("VirtualKeyboardFeedback", cxVKFeedback.isChecked());
			params.putBoolean("TouchInput", cxTouchInput.isChecked());

			params.putInt("VirtualKeyboardType", spVKType.getSelectedItemPosition());
			params.putInt("Layout", spLayout.getSelectedItemPosition());
			params.putInt("ButtonShape", spButtonsShape.getSelectedItemPosition());
			params.putInt("VirtualKeyboardAlpha", sbVKAlpha.getProgress());
			try {
				int value = Integer.parseInt(tfVKHideDelay.getText().toString());
				params.putInt("VirtualKeyboardDelay", value);
			} catch (NumberFormatException e) {
				params.putInt("VirtualKeyboardDelay", 0);
			}
			try {
				int value = Integer.parseInt(tfVKBack.getText().toString(), 16);
				params.putInt("VirtualKeyboardColorBackground", value);
			} catch (Exception ignored) {}
			try {
				int value = Integer.parseInt(tfVKFore.getText().toString(), 16);
				params.putInt("VirtualKeyboardColorForeground", value);
			} catch (Exception ignored) {}
			try {
				int value = Integer.parseInt(tfVKSelBack.getText().toString(), 16);
				params.putInt("VirtualKeyboardColorBackgroundSelected", value);
			} catch (Exception ignored) {}
			try {
				int value = Integer.parseInt(tfVKSelFore.getText().toString(), 16);
				params.putInt("VirtualKeyboardColorForegroundSelected", value);
			} catch (Exception ignored) {}
			try {
				int value = Integer.parseInt(tfVKOutline.getText().toString(), 16);
				params.putInt("VirtualKeyboardColorOutline", value);
			} catch (Exception ignored) {}
			params.putString("SystemProperties", getSystemProperties());

			params.apply();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	@NonNull
	private String getSystemProperties() {
		String s = tfSystemProperties.getText().toString();
		String[] lines = s.split("\\n");
		StringBuilder sb = new StringBuilder(s.length());
		boolean validCharset = false;
		for (int i = lines.length - 1; i >= 0; i--) {
			String line = lines[i];
			if (line.startsWith("microedition.encoding:")) {
				if (validCharset) continue;
				try {
					Charset.forName(line.substring(22).trim());
					validCharset = true;
				} catch (Exception ignored) {
					continue;
				}
			}
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private int getFontSizeForResolution(int sizeType, int width, int height) {
		int size = Math.max(width, height);
		if (size > 0) {
			for (int i = 0; i < SCREEN_SIZES.length; i++) {
				if (SCREEN_SIZES[i] >= size) {
					return FONT_SIZES[i * 3 + sizeType];
				}
			}
		}
		return FONT_SIZES[FONT_SIZES.length - (3 - sizeType)];
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.config, menu);
		if (isProfile) {
			menu.findItem(R.id.action_start).setVisible(false);
			menu.findItem(R.id.action_clear_data).setVisible(false);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_start:
				startMIDlet();
				break;
			case R.id.action_clear_data:
				showClearDataDialog();
				break;
			case R.id.action_reset_settings:
				params.edit().clear().apply();
				loadParams();
				break;
			case R.id.action_reset_layout:
				//noinspection ResultOfMethodCallIgnored
				keylayoutFile.delete();
				loadKeyLayout();
				break;
			case R.id.action_load_profile:
				LoadProfileAlert.newInstance(keylayoutFile.getParent())
						.show(fragmentManager, "load_profile");
				break;
			case R.id.action_save_profile:
				saveParams();
				SaveProfileAlert.getInstance(keylayoutFile.getParent())
						.show(fragmentManager, "save_profile");
				break;
			case R.id.action_map_keys:
				Intent i = new Intent(getIntent());
				i.setClass(getApplicationContext(), KeyMapperActivity.class);
				startActivity(i);
				break;
			case android.R.id.home:
				finish();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showClearDataDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.message_clear_data)
				.setPositiveButton(android.R.string.ok, (d, w) -> FileUtils.clearDirectory(dataDir))
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void startMIDlet() {
		Intent i = new Intent(getIntent());
		i.setClass(getApplicationContext(), MicroActivity.class);
		startActivity(i);
		finish();
	}

	@SuppressLint("SetTextI18n")
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.cmdSwapSizes:
				String tmp = tfScreenWidth.getText().toString();
				tfScreenWidth.setText(tfScreenHeight.getText().toString());
				tfScreenHeight.setText(tmp);
				break;
			case R.id.cmdFontSizePresets:
				new AlertDialog.Builder(this)
						.setTitle(getString(R.string.SIZE_PRESETS))
						.setItems(fontAdapter.toArray(new String[0]),
								(dialog, which) -> {
									tfFontSizeSmall.setText(Integer.toString(fontSmall.get(which)));
									tfFontSizeMedium.setText(Integer.toString(fontMedium.get(which)));
									tfFontSizeLarge.setText(Integer.toString(fontLarge.get(which)));
								})
						.show();
				break;
			case R.id.cmdScreenBack:
				showColorPicker(tfScreenBack);
				break;
			case R.id.cmdVKBack:
				showColorPicker(tfVKBack);
				break;
			case R.id.cmdVKFore:
				showColorPicker(tfVKFore);
				break;
			case R.id.cmdVKSelFore:
				showColorPicker(tfVKSelFore);
				break;
			case R.id.cmdVKSelBack:
				showColorPicker(tfVKSelBack);
				break;
			case R.id.cmdVKOutline:
				showColorPicker(tfVKOutline);
				break;
			default:
		}
	}

	private void showScreenPresets(View v) {
		PopupMenu popup = new PopupMenu(this, v);
		Menu menu = popup.getMenu();
		for (String preset : screenPresets) {
			menu.add(preset);
		}
		popup.setOnMenuItemClickListener(item -> {
			String string = item.getTitle().toString();
			int separator = string.indexOf(" x ");
			tfScreenWidth.setText(string.substring(0, separator));
			tfScreenHeight.setText(string.substring(separator + 3));
			return true;
		});
		popup.show();
	}

	private void showColorPicker(EditText et) {
		AmbilWarnaDialog.OnAmbilWarnaListener colorListener = new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				et.setText(String.format("%06X", color & 0xFFFFFF));
				ColorDrawable drawable = (ColorDrawable) et.getCompoundDrawables()[2];
				drawable.setColor(color);
			}

			@Override
			public void onCancel(AmbilWarnaDialog dialog) {
			}
		};

		int color;
		try {
			color = Integer.parseInt(et.getText().toString().trim(), 16);
		} catch (NumberFormatException ignored) {
			color = 0;
		}
		new AmbilWarnaDialog(this, color | 0xFF000000, colorListener).show();
	}

	private void addResolutionToPresets() {
		String width = tfScreenWidth.getText().toString();
		String height = tfScreenHeight.getText().toString();
		if (width.isEmpty()) width = "-1";
		if (height.isEmpty()) height = "-1";
		int w = Integer.parseInt(width);
		int h = Integer.parseInt(height);
		if (w <= 0 || h <= 0) {
			Toast.makeText(this, R.string.invalid_resolution_not_saved, Toast.LENGTH_SHORT).show();
			return;
		}
		String preset = width + " x " + height;

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Set<String> set = preferences.getStringSet("ResolutionsPreset", null);
		if (set == null) {
			set = new HashSet<>(1);
		}
		if (set.add(preset)) {
			preferences.edit().putStringSet("ResolutionsPreset", set).apply();
			screenPresets.add(preset);
			Toast.makeText(this, getString(R.string.saved, preset), Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, R.string.not_saved_exists, Toast.LENGTH_SHORT).show();
		}
	}
}
