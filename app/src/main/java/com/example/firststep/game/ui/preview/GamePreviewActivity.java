package com.example.firststep.game.ui.preview;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.composition.PreviewHost;
import com.example.firststep.game.domain.PreviewItem;
import com.example.firststep.game.presentation.GameAction;
import com.example.firststep.game.presentation.GameCoordinator;
import com.example.firststep.game.presentation.GameScreenState;

/** Debug-only entry point; selection is transient instance state, never game progress. */
public final class GamePreviewActivity extends AppCompatActivity {
    private static final String SELECTED_ID = "preview_selected_id";
    private PreviewHost host;
    private GameCoordinator coordinator;
    private TextView status;
    private Button itemButton;
    private Button retryButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) { finish(); return; }
        setContentView(R.layout.game_preview);
        View root = findViewById(R.id.game_preview_root);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.game_preview_heading), true);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        status = findViewById(R.id.game_preview_status);
        itemButton = findViewById(R.id.game_preview_item);
        retryButton = findViewById(R.id.game_preview_retry);
        retryButton.setOnClickListener(v -> coordinator.dispatch(GameAction.refresh()));
        host = new PreviewHost(this::render, savedInstanceState == null ? null : savedInstanceState.getString(SELECTED_ID));
        coordinator = host.coordinator();
    }
    @Override protected void onStart() { super.onStart(); if (coordinator != null) coordinator.start(); }
    @Override protected void onStop() { if (coordinator != null) coordinator.stop(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle outState) {
        if (coordinator != null) outState.putString(SELECTED_ID, coordinator.getSelectedId());
        super.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() { if (host != null) host.close(); super.onDestroy(); }
    private void render(GameScreenState state) {
        switch (state.getStatus()) {
            case SELECTED: status.setText(R.string.game_preview_selected); break;
            case READY: status.setText(R.string.game_preview_ready); break;
            case EMPTY: status.setText(R.string.game_preview_empty); break;
            case ERROR: status.setText(R.string.game_preview_error); break;
            default: status.setText(R.string.game_preview_loading);
        }
        retryButton.setVisibility(state.canRefresh() ? View.VISIBLE : View.GONE);
        if (state.getItems().isEmpty()) {
            itemButton.setVisibility(View.GONE);
            itemButton.setOnClickListener(null);
        } else {
            PreviewItem item = state.getItems().get(0);
            itemButton.setText(R.string.game_preview_item);
            itemButton.setVisibility(View.VISIBLE);
            itemButton.setOnClickListener(v -> coordinator.dispatch(GameAction.selectItem(item.getId())));
        }
    }
}
