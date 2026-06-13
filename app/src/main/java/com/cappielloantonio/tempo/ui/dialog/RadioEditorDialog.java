package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogRadioEditorBinding;
import com.cappielloantonio.tempo.databinding.ItemRadioSearchResultBinding;
import com.cappielloantonio.tempo.databinding.PopupRadioSearchBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.RadioCallback;
import com.cappielloantonio.tempo.radiobrowser.RadioBrowserClient;
import com.cappielloantonio.tempo.radiobrowser.RadioBrowserCountry;
import com.cappielloantonio.tempo.radiobrowser.RadioBrowserStation;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.RadioEditorViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RadioEditorDialog extends DialogFragment {
    private DialogRadioEditorBinding bind;
    private RadioEditorViewModel radioEditorViewModel;
    private final RadioCallback radioCallback;

    private String radioName;
    private String radioStreamURL;
    private String radioHomepageURL;
    private String radioCoverArtUrl;

    private PopupWindow searchPopup;
    private PopupRadioSearchBinding popupBind;
    private List<String> countryNames = new ArrayList<>();
    private SearchResultsAdapter searchAdapter;
    private ArrayAdapter<String> countryListAdapter;
    private boolean suppressCountryFilter;

    public RadioEditorDialog(RadioCallback radioCallback) {
        this.radioCallback = radioCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        bind = DialogRadioEditorBinding.inflate(getLayoutInflater());
        // Scope the ViewModel to this dialog (not the activity) so each opening starts with a
        // clean state — otherwise a retained toEdit / isSuccess leaks between add and edit flows.
        radioEditorViewModel = new ViewModelProvider(this).get(RadioEditorViewModel.class);

        if (getArguments() != null && getArguments().getParcelable(Constants.INTERNET_RADIO_STATION_OBJECT) != null) {
            radioEditorViewModel.setRadioToEdit(requireArguments().getParcelable(Constants.INTERNET_RADIO_STATION_OBJECT));
        }

        setupObservers();
        setupSourceToggle();
        setupSearchButton();

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(bind.getRoot())
                .setTitle(R.string.radio_editor_dialog_title)
                // The real click handler is set in onStart() so it does not auto-dismiss the
                // dialog: on a server failure we keep it open so the user can switch to Local.
                .setPositiveButton(R.string.radio_editor_dialog_positive_button, (dialog, id) -> {})
                .setNeutralButton(R.string.radio_editor_dialog_neutral_button, (dialog, id) -> {
                    radioEditorViewModel.deleteRadio();
                })
                .setNegativeButton(R.string.radio_editor_dialog_negative_button, (dialog, id) -> {
                    dialog.cancel();
                })
                .create();
    }

    private void setupSourceToggle() {
        if (radioEditorViewModel.isEditing()) {
            bind.sourceToggleLayout.setVisibility(View.GONE);
            bind.searchDirectoryButton.setVisibility(View.GONE);
        } else {
            bind.sourceToggleLayout.setVisibility(View.VISIBLE);
            bind.searchDirectoryButton.setVisibility(View.VISIBLE);
            bind.radioSourceToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    boolean isLocal = checkedId == R.id.radio_source_local;
                    radioEditorViewModel.setLocal(isLocal);
                    bind.internetRadioStationCoverArtUrlLayout.setVisibility(isLocal ? View.VISIBLE : View.GONE);
                }
            });
            bind.radioSourceToggle.check(R.id.radio_source_server);
        }
    }

    private void setupSearchButton() {
        bind.searchDirectoryButton.setOnClickListener(v -> {
            if (searchPopup != null && searchPopup.isShowing()) {
                searchPopup.dismiss();
                return;
            }
            showSearchPopup();
        });
    }

    private void showSearchPopup() {
        popupBind = PopupRadioSearchBinding.inflate(getLayoutInflater());

        searchAdapter = new SearchResultsAdapter(station -> {
            fillFromSearchResult(station);
            searchPopup.dismiss();
        });
        popupBind.popupResultsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        popupBind.popupResultsRecycler.setAdapter(searchAdapter);

        popupBind.popupSearchName.setText(bind.internetRadioStationNameTextView.getText());

        setupPopupCountryDropdown();

        popupBind.popupCancelButton.setOnClickListener(v -> searchPopup.dismiss());

        popupBind.popupSearchButton.setOnClickListener(v -> {
            String query = Objects.requireNonNull(popupBind.popupSearchName.getText()).toString().trim();
            String country = popupBind.popupCountryDropdown.getText() != null
                    ? popupBind.popupCountryDropdown.getText().toString().trim() : "";
            if (query.isEmpty() && country.isEmpty()) {
                popupBind.popupSearchName.setError(getString(R.string.radio_search_empty_query));
                return;
            }
            hideCountryList();
            performSearch(query, country);
        });

        Rect windowRect = new Rect();
        requireActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(windowRect);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        int popupWidth = windowRect.width() - dp16 * 2;
        int maxPopupHeight = (int) (windowRect.height() * 0.6);

        searchPopup = new PopupWindow(popupBind.getRoot(), popupWidth, maxPopupHeight, true);
        searchPopup.setFocusable(true);
        searchPopup.setElevation(16f);
        searchPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        searchPopup.setClippingEnabled(true);
        searchPopup.setOnDismissListener(() -> searchPopup = null);
        // The popup attaches to the dialog window's token; the dialog (MaterialAlertDialog) is
        // itself centered on screen, so Gravity.CENTER reliably centers the popup on screen.
        searchPopup.showAtLocation(bind.getRoot(), Gravity.CENTER, 0, 0);

        String initialQuery = Objects.requireNonNull(popupBind.popupSearchName.getText()).toString().trim();
        if (!initialQuery.isEmpty()) {
            performSearch(initialQuery, "");
        }
    }

    // The country picker is an inline list rendered inside the popup (not a floating
    // ListPopupWindow), so we fully control its position — it overlays the results area,
    // is bounded by the popup and scrolls, and can never flip off-screen.
    private void setupPopupCountryDropdown() {
        countryListAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_country_dropdown);
        popupBind.popupCountryList.setAdapter(countryListAdapter);

        popupBind.popupCountryList.setOnItemClickListener((parent, view, position, id) -> {
            String selected = countryListAdapter.getItem(position);
            suppressCountryFilter = true;
            popupBind.popupCountryDropdown.setText(selected);
            popupBind.popupCountryDropdown.setSelection(selected != null ? selected.length() : 0);
            suppressCountryFilter = false;
            hideCountryList();
        });

        popupBind.popupCountryDropdown.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateCountryEndIcon();
                if (suppressCountryFilter || popupBind == null) return;
                showCountryList(s != null ? s.toString() : "");
            }
        });

        popupBind.popupCountryDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showCountryList(popupBind.popupCountryDropdown.getText() != null
                        ? popupBind.popupCountryDropdown.getText().toString() : "");
            }
        });

        // The end icon doubles as a dropdown toggle (when empty) and a clear button (when filled).
        popupBind.popupCountryLayout.setEndIconOnClickListener(v -> {
            CharSequence text = popupBind.popupCountryDropdown.getText();
            if (text != null && text.length() > 0) {
                popupBind.popupCountryDropdown.setText("");
                popupBind.popupCountryDropdown.requestFocus();
            } else if (popupBind.popupCountryList.getVisibility() == View.VISIBLE) {
                hideCountryList();
            } else {
                popupBind.popupCountryDropdown.requestFocus();
                showCountryList("");
            }
        });
        updateCountryEndIcon();

        // Hide the country list when the user moves to the name field.
        popupBind.popupSearchName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideCountryList();
        });

        if (!countryNames.isEmpty()) return;

        RadioBrowserClient.getInstance().getCountries().enqueue(new retrofit2.Callback<List<RadioBrowserCountry>>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<List<RadioBrowserCountry>> call, @NonNull retrofit2.Response<List<RadioBrowserCountry>> response) {
                if (!isAdded() || popupBind == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    countryNames.clear();
                    for (RadioBrowserCountry c : response.body()) {
                        if (c.stationCount > 0) {
                            countryNames.add(c.name);
                        }
                    }
                    Collections.sort(countryNames, String.CASE_INSENSITIVE_ORDER);
                    if (popupBind.popupCountryList.getVisibility() == View.VISIBLE) {
                        showCountryList(popupBind.popupCountryDropdown.getText() != null
                                ? popupBind.popupCountryDropdown.getText().toString() : "");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<List<RadioBrowserCountry>> call, @NonNull Throwable t) {
            }
        });
    }

    private void showCountryList(String query) {
        if (popupBind == null) return;
        String q = query != null ? query.trim().toLowerCase() : "";
        List<String> filtered = new ArrayList<>();
        for (String name : countryNames) {
            if (q.isEmpty() || name.toLowerCase().contains(q)) filtered.add(name);
        }
        countryListAdapter.clear();
        countryListAdapter.addAll(filtered);
        countryListAdapter.notifyDataSetChanged();
        positionCountryList();
        popupBind.popupCountryList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // Left-align the list with the country input field (whose x is dynamic because the name
    // field uses a layout weight) and extend it to the popup's right edge.
    private void positionCountryList() {
        if (popupBind == null) return;
        View parent = (View) popupBind.popupCountryList.getParent();
        int[] parentLocation = new int[2];
        parent.getLocationOnScreen(parentLocation);
        int[] fieldLocation = new int[2];
        popupBind.popupCountryLayout.getLocationOnScreen(fieldLocation);

        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) popupBind.popupCountryList.getLayoutParams();
        params.width = FrameLayout.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.max(0, fieldLocation[0] - parentLocation[0]);
        params.rightMargin = dp16;
        popupBind.popupCountryList.setLayoutParams(params);
    }

    private void hideCountryList() {
        if (popupBind != null) popupBind.popupCountryList.setVisibility(View.GONE);
    }

    private void updateCountryEndIcon() {
        if (popupBind == null) return;
        CharSequence text = popupBind.popupCountryDropdown.getText();
        boolean hasText = text != null && text.length() > 0;
        popupBind.popupCountryLayout.setEndIconDrawable(hasText ? R.drawable.ic_close : R.drawable.ic_expand_more);
    }

    private void performSearch(String query, String country) {
        if (searchAdapter.getItemCount() == 0) {
            popupBind.popupProgressBar.setVisibility(View.VISIBLE);
            popupBind.popupEmptyResults.setVisibility(View.GONE);
        }

        retrofit2.Call<List<RadioBrowserStation>> call;
        if (!query.isEmpty() && !country.isEmpty()) {
            call = RadioBrowserClient.getInstance().searchAdvanced(query, country, null, null);
        } else if (!country.isEmpty()) {
            call = RadioBrowserClient.getInstance().searchByCountryExact(country);
        } else {
            call = RadioBrowserClient.getInstance().searchByName(query);
        }

        call.enqueue(new retrofit2.Callback<List<RadioBrowserStation>>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<List<RadioBrowserStation>> call, @NonNull retrofit2.Response<List<RadioBrowserStation>> response) {
                if (!isAdded() || popupBind == null) return;
                popupBind.popupProgressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    searchAdapter.setItems(response.body());
                    popupBind.popupResultsRecycler.setVisibility(View.VISIBLE);
                    popupBind.popupEmptyResults.setVisibility(View.GONE);
                } else {
                    searchAdapter.setItems(Collections.emptyList());
                    popupBind.popupResultsRecycler.setVisibility(View.GONE);
                    popupBind.popupEmptyResults.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<List<RadioBrowserStation>> call, @NonNull Throwable t) {
                if (!isAdded() || popupBind == null) return;
                popupBind.popupProgressBar.setVisibility(View.GONE);
                popupBind.popupEmptyResults.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), getString(R.string.radio_search_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fillFromSearchResult(RadioBrowserStation station) {
        String streamUrl = station.urlResolved != null ? station.urlResolved : station.url;
        bind.internetRadioStationNameTextView.setText(station.name);
        bind.internetRadioStationStreamUrlTextView.setText(streamUrl);
        bind.internetRadioStationHomepageUrlTextView.setText(station.homepage);

        String coverArtUrl = (station.favicon != null && !station.favicon.isEmpty()) ? station.favicon : null;
        bind.internetRadioStationCoverArtUrlTextView.setText(coverArtUrl);
    }

    private void setupObservers() {
        radioEditorViewModel.getIsSuccess().observe(this, isSuccess -> {
            if (isSuccess != null && isSuccess) {
                Toast.makeText(requireContext(),
                    radioEditorViewModel.getRadioToEdit() == null ?
                            App.getContext().getString(R.string.radio_editor_dialog_added) : App.getContext().getString(R.string.radio_editor_dialog_updated),
                    Toast.LENGTH_SHORT).show();
                dismissDialog();
            }
        });
        radioEditorViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                radioEditorViewModel.clearError();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        setParameterInfo();

        // Override the positive button so clicking it does not auto-dismiss the dialog; we
        // dismiss manually only on success (see the isSuccess observer). This keeps the dialog
        // open on validation errors or when the server rejects the station.
        if (getDialog() instanceof androidx.appcompat.app.AlertDialog) {
            androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) getDialog();
            android.widget.Button positiveButton = alertDialog.getButton(android.app.Dialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setOnClickListener(v -> performSave());
            }
        }
    }

    private void performSave() {
        if (!validateInput()) return;

        if (radioEditorViewModel.getRadioToEdit() == null) {
            radioEditorViewModel.createRadio(radioName, radioStreamURL,
                    radioHomepageURL.isEmpty() ? null : radioHomepageURL,
                    radioCoverArtUrl.isEmpty() ? null : radioCoverArtUrl);
        } else {
            radioEditorViewModel.updateRadio(radioName, radioStreamURL,
                    radioHomepageURL.isEmpty() ? null : radioHomepageURL,
                    radioCoverArtUrl.isEmpty() ? null : radioCoverArtUrl);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchPopup != null) {
            searchPopup.dismiss();
        }
        bind = null;
    }

    private void setParameterInfo() {
        InternetRadioStation toEdit = radioEditorViewModel.getRadioToEdit();
        if (toEdit != null) {
            bind.internetRadioStationNameTextView.setText(toEdit.getName());
            bind.internetRadioStationStreamUrlTextView.setText(toEdit.getStreamUrl());
            bind.internetRadioStationHomepageUrlTextView.setText(toEdit.getHomePageUrl());

            bind.sourceToggleLayout.setVisibility(View.GONE);
            bind.searchDirectoryButton.setVisibility(View.GONE);

            if (radioEditorViewModel.isLocal()) {
                bind.internetRadioStationCoverArtUrlLayout.setVisibility(View.VISIBLE);
                bind.internetRadioStationCoverArtUrlTextView.setText(toEdit.getCoverArt());
            }

            if (getDialog() != null) {
                androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) getDialog();
                android.widget.Button neutralButton = alertDialog.getButton(android.app.Dialog.BUTTON_NEUTRAL);
                if (neutralButton != null) {
                    neutralButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private boolean validateInput() {
        radioName = Objects.requireNonNull(bind.internetRadioStationNameTextView.getText()).toString().trim();
        radioStreamURL = Objects.requireNonNull(bind.internetRadioStationStreamUrlTextView.getText()).toString().trim();
        radioHomepageURL = Objects.requireNonNull(bind.internetRadioStationHomepageUrlTextView.getText()).toString().trim();
        radioCoverArtUrl = bind.internetRadioStationCoverArtUrlTextView.getText() != null
                ? bind.internetRadioStationCoverArtUrlTextView.getText().toString().trim() : "";
        if (TextUtils.isEmpty(radioName)) {
            bind.internetRadioStationNameTextView.setError(getString(R.string.error_required));
            return false;
        }
        if (TextUtils.isEmpty(radioStreamURL)) {
            bind.internetRadioStationStreamUrlTextView.setError(getString(R.string.error_required));
            return false;
        }
        return true;
    }

    private void dismissDialog() {
        if (radioCallback != null) {
            radioCallback.onDismiss();
        }
        Objects.requireNonNull(getDialog()).dismiss();
    }

    private static class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {
        private List<RadioBrowserStation> stations = Collections.emptyList();
        private final OnStationClickListener listener;

        interface OnStationClickListener {
            void onClick(RadioBrowserStation station);
        }

        SearchResultsAdapter(OnStationClickListener listener) {
            this.listener = listener;
        }

        void setItems(List<RadioBrowserStation> items) {
            stations = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemRadioSearchResultBinding binding = ItemRadioSearchResultBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RadioBrowserStation station = stations.get(position);

            holder.binding.stationNameTextView.setText(station.name);

            StringBuilder details = new StringBuilder();
            if (station.country != null && !station.country.isEmpty()) {
                details.append(station.country);
            }
            if (station.codec != null && !station.codec.isEmpty()) {
                if (details.length() > 0) details.append(" · ");
                details.append(station.codec);
            }
            if (station.bitrate > 0) {
                if (details.length() > 0) details.append(" · ");
                details.append(station.bitrate).append(" kbps");
            }
            holder.binding.stationDetailsTextView.setText(details.toString());

            String coverUrl = station.favicon;
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(coverUrl)
                        .apply(CustomGlideRequest.createRequestOptions(
                                holder.itemView.getContext(), coverUrl, CustomGlideRequest.ResourceType.Radio))
                        .into(holder.binding.stationCoverImageView);
            } else {
                holder.binding.stationCoverImageView.setImageDrawable(null);
            }

            holder.binding.addStationButton.setText(R.string.radio_search_use);
            holder.binding.addStationButton.setOnClickListener(v -> listener.onClick(station));
        }

        @Override
        public int getItemCount() {
            return stations.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemRadioSearchResultBinding binding;

            ViewHolder(ItemRadioSearchResultBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
