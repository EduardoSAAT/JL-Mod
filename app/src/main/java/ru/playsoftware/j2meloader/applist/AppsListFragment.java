/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2018 Nikita Shakarun
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

package ru.playsoftware.j2meloader.applist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.ListFragment;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ConfigActivity;
import ru.playsoftware.j2meloader.config.TemplatesActivity;
import ru.playsoftware.j2meloader.donations.DonationsActivity;
import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerActivity;
import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerFragment;
import ru.playsoftware.j2meloader.info.AboutDialogFragment;
import ru.playsoftware.j2meloader.info.HelpDialogFragment;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.AppUtils;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.woesss.j2me.installer.InstallerDialog;

public class AppsListFragment extends ListFragment {
	private AppRepository appRepository;
	private CompositeDisposable compositeDisposable;
	private AppsListAdapter adapter;
	private String appSort;
	private String appPath;
	private static final int FILE_CODE = 0;
	private Uri appUri;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		compositeDisposable = new CompositeDisposable();
		adapter = new AppsListAdapter(getActivity());
		Bundle args = getArguments();
		if (args == null) {
			return;
		}
		appSort = args.getString(MainActivity.APP_SORT_KEY);
		appPath = args.getString(MainActivity.APP_PATH_KEY);
		appUri = args.getParcelable(MainActivity.APP_URI_KEY);
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_appslist, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		registerForContextMenu(getListView());
		setHasOptionsMenu(true);
		setListAdapter(adapter);
		initDb();
		FloatingActionButton fab = requireActivity().findViewById(R.id.fab);
		fab.setOnClickListener(v -> {
			Intent i = new Intent(getActivity(), FilteredFilePickerActivity.class);
			i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
			i.putExtra(FilePickerActivity.EXTRA_SINGLE_CLICK, true);
			i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
			i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
			i.putExtra(FilePickerActivity.EXTRA_START_PATH, FilteredFilePickerFragment.getLastPath());
			startActivityForResult(i, FILE_CODE);
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		if (appPath != null) {
			installApp(appPath, appUri);
			appPath = null;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		compositeDisposable.clear();
	}

	@SuppressLint("CheckResult")
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void initDb() {
		appRepository = new AppRepository(requireActivity().getApplication(), appSort.equals("date"));
		ConnectableFlowable<List<AppItem>> listConnectableFlowable = appRepository.getAll()
				.subscribeOn(Schedulers.io()).publish();
		listConnectableFlowable
				.firstElement()
				.subscribe(list -> AppUtils.updateDb(appRepository, list));
		listConnectableFlowable
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(list -> adapter.setItems(list));
		compositeDisposable.add(listConnectableFlowable.connect());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == FILE_CODE && resultCode == Activity.RESULT_OK) {
			List<Uri> files = Utils.getSelectedFilesFromResult(data);
			for (Uri uri : files) {
				File file = Utils.getFileForUri(uri);
				installApp(file.getAbsolutePath(), null);
			}
		}
	}

	private void installApp(String path, Uri uri) {
		InstallerDialog.newInstance(path, uri).show(getParentFragmentManager(), "installer");
	}

	private void alertRename(final int id) {
		AppItem item = adapter.getItem(id);
		EditText editText = new EditText(getActivity());
		editText.setText(item.getTitle());
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(getContext());
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(R.string.action_context_rename)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					String title = editText.getText().toString().trim();
					if (title.equals("")) {
						Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
					} else {
						item.setTitle(title);
						appRepository.insert(item);
					}
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void alertDelete(final int id) {
		AppItem item = adapter.getItem(id);
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.message_delete)
				.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
					AppUtils.deleteApp(item);
					appRepository.delete(item);
				})
				.setNegativeButton(android.R.string.no, null);
		builder.show();
	}

	@Override
	public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
		AppItem item = adapter.getItem(position);
		Config.startApp(getActivity(), item, false);
	}

	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
									ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = requireActivity().getMenuInflater();
		inflater.inflate(R.menu.context_main, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		int index = info.position;
		AppItem appItem = adapter.getItem(index);
		switch (item.getItemId()) {
			case R.id.action_context_shortcut:
				Bitmap bitmap = BitmapFactory.decodeFile(appItem.getImagePathExt());
				Intent launchIntent = new Intent(Intent.ACTION_DEFAULT,
						Uri.parse(appItem.getPath()), getActivity(), ConfigActivity.class);
				launchIntent.putExtra(ConfigActivity.MIDLET_NAME_KEY, appItem.getTitle());
				ShortcutInfoCompat.Builder shortcutInfoCompatBuilder =
						new ShortcutInfoCompat.Builder(requireActivity(), appItem.getTitle())
								.setIntent(launchIntent)
								.setShortLabel(appItem.getTitle());
				if (bitmap != null) {
					shortcutInfoCompatBuilder.setIcon(IconCompat.createWithBitmap(bitmap));
				} else {
					IconCompat icon = IconCompat.createWithResource(requireActivity(),
							R.mipmap.ic_launcher);
					shortcutInfoCompatBuilder.setIcon(icon);
				}
				ShortcutManagerCompat.requestPinShortcut(requireActivity(),
						shortcutInfoCompatBuilder.build(), null);
				break;
			case R.id.action_context_rename:
				alertRename(index);
				break;
			case R.id.action_context_settings:
				Config.startApp(getActivity(), appItem, true);
				break;
			case R.id.action_context_delete:
				alertDelete(index);
				break;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.main, menu);
		final MenuItem searchItem = menu.findItem(R.id.action_search);
		SearchView searchView = (SearchView) searchItem.getActionView();
		Disposable searchViewDisposable = Observable.create((ObservableOnSubscribe<String>) emitter ->
				searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
					@Override
					public boolean onQueryTextSubmit(String query) {
						emitter.onNext(query);
						return true;
					}

					@Override
					public boolean onQueryTextChange(String newText) {
						emitter.onNext(newText);
						return true;
					}
				})).debounce(300, TimeUnit.MILLISECONDS)
				.map(String::toLowerCase)
				.distinctUntilChanged()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(charSequence -> adapter.getFilter().filter(charSequence));
		compositeDisposable.add(searchViewDisposable);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_about:
				AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
				aboutDialogFragment.show(getChildFragmentManager(), "about");
				break;
			case R.id.action_settings:
				Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
				startActivity(settingsIntent);
				break;
			case R.id.action_templates:
				Intent templatesIntent = new Intent(getActivity(), TemplatesActivity.class);
				startActivity(templatesIntent);
				break;
			case R.id.action_help:
				HelpDialogFragment helpDialogFragment = new HelpDialogFragment();
				helpDialogFragment.show(getChildFragmentManager(), "help");
				break;
			case R.id.action_donate:
				Intent donationsIntent = new Intent(getActivity(), DonationsActivity.class);
				startActivity(donationsIntent);
				break;
			case R.id.action_save_log:
				try {
					LogUtils.writeLog();
					Toast.makeText(getActivity(), R.string.log_saved, Toast.LENGTH_SHORT).show();
				} catch (IOException e) {
					e.printStackTrace();
					Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.action_exit_app:
				requireActivity().finish();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

}
