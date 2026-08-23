package com.eddyizm.tempus.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.ui.login.LoginActivity;
import com.eddyizm.tempus.ui.adapter.ServerAdapter;
import com.eddyizm.tempus.databinding.FragmentLoginBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.interfaces.SystemCallback;
import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.repository.SystemRepository;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.dialog.ServerSignupDialog;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.LoginViewModel;

@UnstableApi
public class LoginFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LoginFragment";

    private FragmentLoginBinding bind;
    private MainActivity activity;
    private LoginViewModel loginViewModel;

    private ServerAdapter serverAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.login_page_menu, menu);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        loginViewModel = new ViewModelProvider(requireActivity()).get(LoginViewModel.class);
        bind = FragmentLoginBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        initAppBar();
        initServerListView();
        initNewLoginButton();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (bind == null) return;

            if ((bind.serverInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.login_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void initServerListView() {
        bind.serverListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.serverListRecyclerView.setHasFixedSize(true);

        serverAdapter = new ServerAdapter(this);
        bind.serverListRecyclerView.setAdapter(serverAdapter);
        loginViewModel.getServerList().observe(getViewLifecycleOwner(), servers -> {
            if (!servers.isEmpty()) {
                if (bind != null) bind.noServerAddedTextView.setVisibility(View.GONE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.VISIBLE);
                serverAdapter.setItems(servers);
            } else {
                if (bind != null) bind.noServerAddedTextView.setVisibility(View.VISIBLE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    public void initNewLoginButton() {

        /* Edge-to-edge fixup */
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) bind.newLoginButton.getLayoutParams();
        final int baseBottomMargin = params.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(bind.newLoginButton, (v, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            params.bottomMargin = baseBottomMargin + systemInsets.bottom;
            v.setLayoutParams(params);
            return windowInsets;
        });

        /* Setup button */
        bind.newLoginButton.setOnClickListener(v -> {
            Intent tempus = new Intent(requireActivity(), LoginActivity.class);
            tempus.putExtra("HIDE_TAB_LAYOUT", true);
            tempus.putExtra("HIDE_TOPAPPBAR_LAYOUT", false);
            tempus.putExtra("SELECT_FRAGMENT", 3);
            startActivity(tempus);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            ServerSignupDialog dialog = new ServerSignupDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        }

        return false;
    }

    @Override
    public void onServerClick(Bundle bundle) {
        Server server = bundle.getParcelable("server_object");
        saveServerPreference(server.getServerId(), server.getAddress(), server.getLocalAddress(), server.getUsername(), server.getPassword(), server.isLowSecurity(), server.getClientCert());

        SystemRepository systemRepository = new SystemRepository();
        systemRepository.checkUserCredential(new SystemCallback() {
            @Override
            public void onError(Exception exception) {
                Preferences.switchInUseServerAddress();
                resetServerPreference();
                if (requireContext() != null) { // Swapping activities does not ensure non-null
                    Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSuccess(String password, String token, String salt) {
                activity.goFromLogin();
            }
        });
    }

    @Override
    public void onServerLongClick(Bundle bundle) {
        ServerSignupDialog dialog = new ServerSignupDialog();
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private void saveServerPreference(String serverId, String server, String localAddress, String user, String password, boolean isLowSecurity, String clientCert) {
        Preferences.setServerId(serverId);
        Preferences.setServer(server);
        Preferences.setLocalAddress(localAddress);
        Preferences.setUser(user);
        Preferences.setPassword(password);
        Preferences.setLowSecurity(isLowSecurity);
        Preferences.setClientCert(clientCert);

        App.getSubsonicClientInstance(true);
    }

    private void resetServerPreference() {
        Preferences.setServerId(null);
        Preferences.setServer(null);
        Preferences.setUser(null);
        Preferences.setPassword(null);
        Preferences.setToken(null);
        Preferences.setSalt(null);
        Preferences.setLowSecurity(false);
        Preferences.setClientCert(null);

        App.getSubsonicClientInstance(true);
    }
}
