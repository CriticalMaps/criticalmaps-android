package de.stephanlindauer.criticalmaps.fragments;

import static de.stephanlindauer.criticalmaps.utils.AxtUtils.hideKeyBoard;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.R;
import de.stephanlindauer.criticalmaps.adapter.ChatMessageAdapter;
import de.stephanlindauer.criticalmaps.databinding.FragmentChatBinding;
import de.stephanlindauer.criticalmaps.events.NetworkConnectivityChangedEvent;
import de.stephanlindauer.criticalmaps.events.NewServerResponseEvent;
import de.stephanlindauer.criticalmaps.interfaces.IChatMessage;
import de.stephanlindauer.criticalmaps.model.ChatModel;
import de.stephanlindauer.criticalmaps.model.chat.OutgoingChatMessage;
import de.stephanlindauer.criticalmaps.provider.EventBus;
import de.stephanlindauer.criticalmaps.utils.AxtUtils.SimpleTextWatcher;

public class ChatFragment extends Fragment {
    @Inject
    ChatModel chatModel;

    @Inject
    EventBus eventBus;

    private boolean isTextInputEnabled = true;
    private ChatMessageAdapter chatMessageAdapter;
    private FragmentChatBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        App.components().inject(this);
        binding = FragmentChatBinding.inflate(inflater, container, false);

        binding.chatMessagesRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(final Bundle savedState) {
        super.onActivityCreated(savedState);

        chatMessageAdapter = new ChatMessageAdapter(new ArrayList<>());
        binding.chatMessagesRecyclerview.setAdapter(chatMessageAdapter);
        displayNewData();

        binding.chatMessageTextinputlayout.setCounterMaxLength(IChatMessage.MAX_LENGTH);
        binding.chatMessageEdittext.setFilters(
                new InputFilter[]{new InputFilter.LengthFilter(IChatMessage.MAX_LENGTH)});
        binding.chatMessageEdittext.setOnEditorActionListener(
                (v, actionId, event) -> handleEditorAction(actionId));

        binding.chatSendButton.setOnClickListener(v -> handleSendClicked());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        final String message = binding.chatMessageEdittext.getText().toString();
        binding.chatSendButton.setEnabled(!message.trim().isEmpty());

        binding.chatMessageEdittext.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSendButtonEnabledState();
            }
        });
    }

    private void setSendButtonEnabledWithAnimation(final boolean enabled) {
        if (binding.chatSendButton.isEnabled() == enabled) {
            return;
        }

        final AnimatorSet animatorSet = (AnimatorSet) AnimatorInflater.loadAnimator(getActivity(),
                R.animator.chat_fab_state_change);
        animatorSet.setTarget(binding.chatSendButton);

        // flip button state for color change after first half of the animation
        final ArrayList<Animator> animations = animatorSet.getChildAnimations();
        animations.get(0).addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                binding.chatSendButton.setEnabled(enabled);
            }
        });

        animatorSet.start();
    }

    private boolean handleEditorAction(int actionId) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            handleSendClicked();
            return true;
        }
        return false;
    }

    private void handleSendClicked() {
        final String message = binding.chatMessageEdittext.getText().toString().trim();

        if (message.isEmpty()) {
            return;
        }

        chatModel.setNewOutgoingMessage(new OutgoingChatMessage(message));

        binding.chatMessageEdittext.setText("");
        displayNewData();
    }

    private void displayNewData() {
        final List<IChatMessage> savedAndOutgoingMessages = chatModel.getSavedAndOutgoingMessages();
        chatMessageAdapter.updateData(savedAndOutgoingMessages);

        if (binding.chatMessagesRecyclerview.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
            binding.chatMessagesRecyclerview.scrollToPosition(savedAndOutgoingMessages.size() - 1);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        displayNewData();
        eventBus.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        eventBus.unregister(this);
        hideKeyBoard(binding.chatMessageEdittext);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void handleNewServerData(NewServerResponseEvent e) {
        displayNewData();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void handleNetworkConnectivityChanged(NetworkConnectivityChangedEvent e) {
        setTextInputState(e.isConnected);
    }

    private void setTextInputState(final boolean dataEnabled) {
        if (!dataEnabled) {
            setSendButtonEnabledWithAnimation(false);
            binding.chatMessageEdittext.setEnabled(false);
            binding.chatMessageTextinputlayout.setHint(getString(R.string.chat_no_data_connection_hint));
            isTextInputEnabled = false;
        } else if (!isTextInputEnabled) {
            updateSendButtonEnabledState();
            binding.chatMessageEdittext.setEnabled(true);
            binding.chatMessageTextinputlayout.setHint(getString(R.string.chat_text));
            isTextInputEnabled = true;
        }
    }

    private void updateSendButtonEnabledState() {
        final String message = binding.chatMessageEdittext.getText().toString();
        setSendButtonEnabledWithAnimation(!message.trim().isEmpty());
    }
}
