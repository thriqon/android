package com.twofours.surespot.chat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.MessageDecryptor;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.ImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatAdapter extends BaseAdapter {
	private final static String TAG = "ChatAdapter";
	private ArrayList<SurespotMessage> mMessages = new ArrayList<SurespotMessage>();
	private Context mContext;
	private final static int TYPE_US = 0;
	private final static int TYPE_THEM = 1;
	private boolean mLoading;
	private IAsyncCallback<Boolean> mLoadingCallback;
	private boolean mDebugMode;
	private boolean mCheckingSequence;

	public ChatAdapter(Context context) {
		SurespotLog.v(TAG, "Constructor.");
		mContext = context;
		SharedPreferences pm = context.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
		mDebugMode = pm.getBoolean("pref_debug_mode", false);
		// pm.getBoolean("pref_hide_deleted_messages", false);

	}

	public void doneCheckingSequence() {
		mCheckingSequence = false;
	}

	public boolean isLoading() {
		return mLoading;
	}

	public void setLoading(boolean loading) {
		mLoading = loading;
		if (mLoadingCallback != null) {
			mLoadingCallback.handleResponse(loading);
		}
	}

	public void setLoadingCallback(IAsyncCallback<Boolean> callback) {
		mLoadingCallback = callback;
	}

	public void evictCache() {
		ImageDownloader.evictCache();
	}

	public ArrayList<SurespotMessage> getMessages() {
		return mMessages;
	}

	// get the last message that has an id
	public SurespotMessage getLastMessageWithId() {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			SurespotMessage message = iterator.previous();
			if (message.getId() != null && message.getId() > 0) {
				return message;
			}
		}
		return null;
	}

	public SurespotMessage getFirstMessageWithId() {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(0); iterator.hasNext();) {
			SurespotMessage message = iterator.next();
			if (message.getId() != null && message.getId() > 0) {
				return message;
			}
		}
		return null;
	}

	// update the id and sent status of the message once we received
	private boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort)
			throws SurespotMessageSequenceException {

		// SurespotLog.v(TAG, "addMessage, could not find message");

		// make sure message is in sequence
		//
		// if (!mCheckingSequence && checkSequence && (message.getId() != null)) {
		// SurespotMessage previousMessage = getLastMessageWithId();
		//
		// int previousId = 0;
		// if (previousMessage != null) {
		// previousId = previousMessage.getId();
		// }
		//
		// if (previousId != (message.getId() - 1)) {
		// throw new SurespotMessageSequenceException(previousId);
		// }
		//
		// }

		int index = mMessages.indexOf(message);
		boolean added = false;
		if (index == -1) {

			mMessages.add(message);
			added = true;
		}

		else {
			// SurespotLog.v(TAG, "addMessage, updating message");
			SurespotMessage updateMessage = mMessages.get(index);
			if (message.getId() != null) {
				updateMessage.setId(message.getId());
			}
			if (message.getDateTime() != null) {
				updateMessage.setDateTime(message.getDateTime());
			}
			if (message.getData() != null) {
				updateMessage.setData(message.getData());
			}

		}

		if (sort) {
			sort();
		}
		return added;
	}

	private void insertMessage(SurespotMessage message) {
		// if (mMessages.indexOf(message) == -1) {
		mMessages.add(0, message);
		// }
		// else {
		// SurespotLog.v(TAG, "insertMessage, message already present");
		// }
	}

	public synchronized void setMessages(ArrayList<SurespotMessage> messages) {
		if (messages.size() > 0) {
			mMessages.clear();
			mMessages.addAll(messages);

			// notifyDataSetChanged();
		}

	}

	// public void addMessages(ArrayList<SurespotMessage> messages) {
	// if (messages.size() > 0) {
	// mMessages.addAll(messages);
	//
	// // notifyDataSetChanged();
	// }
	// }

	// thanks to http://www.sherif.mobi/2012/01/listview-with-ability-to-hide-rows.html
	@Override
	public int getCount() {

		return mMessages.size();

	}

	@Override
	public Object getItem(int position) {
		return mMessages.get(position);
	}

	@Override
	public int getItemViewType(int position) {
		SurespotMessage message = mMessages.get(position);
		String otherUser = ChatUtils.getOtherUser(message.getFrom(), message.getTo());
		if (otherUser.equals(message.getFrom())) {
			return TYPE_THEM;
		}
		else {
			return TYPE_US;
		}
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// SurespotLog.v(TAG, "getView, pos: " + position);
		final int type = getItemViewType(position);
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final ChatMessageViewHolder chatMessageViewHolder;
		if (convertView == null) {
			chatMessageViewHolder = new ChatMessageViewHolder();

			switch (type) {
			case TYPE_US:
				convertView = inflater.inflate(R.layout.message_list_item_us, parent, false);
				chatMessageViewHolder.vMessageSending = convertView.findViewById(R.id.messageSending);
				chatMessageViewHolder.vMessageSent = convertView.findViewById(R.id.messageSent);
				break;
			case TYPE_THEM:
				convertView = inflater.inflate(R.layout.message_list_item_them, parent, false);
				break;
			}

			chatMessageViewHolder.tvTime = (TextView) convertView.findViewById(R.id.messageTime);
			chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);
			chatMessageViewHolder.imageView = (ImageView) convertView.findViewById(R.id.messageImage);
			chatMessageViewHolder.ivNotShareable = (ImageView) convertView.findViewById(R.id.messageImageNotShareable);
			chatMessageViewHolder.ivShareable = (ImageView) convertView.findViewById(R.id.messageImageShareable);

			if (mDebugMode) {
				chatMessageViewHolder.tvId = (TextView) convertView.findViewById(R.id.messageId);
				chatMessageViewHolder.tvToVersion = (TextView) convertView.findViewById(R.id.messageToVersion);
				chatMessageViewHolder.tvFromVersion = (TextView) convertView.findViewById(R.id.messageFromVersion);
				chatMessageViewHolder.tvIv = (TextView) convertView.findViewById(R.id.messageIv);
				chatMessageViewHolder.tvData = (TextView) convertView.findViewById(R.id.messageData);
				chatMessageViewHolder.tvMimeType = (TextView) convertView.findViewById(R.id.messageMimeType);
			}

			convertView.setTag(chatMessageViewHolder);
		}
		else {
			chatMessageViewHolder = (ChatMessageViewHolder) convertView.getTag();
		}

		final SurespotMessage item = (SurespotMessage) getItem(position);

		boolean deleted = false;
		if (item.getId() == null) {
			chatMessageViewHolder.tvTime.setText("sending...");
		}
		else {

			if (item.getPlainData() == null) {
				chatMessageViewHolder.tvTime.setText("loading and decrypting...");
			}
			else {

				if (item.getDateTime() != null) {

					chatMessageViewHolder.tvTime.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
							item.getDateTime()));
				}
				else {
					chatMessageViewHolder.tvTime.setText("");
				}
			}

		}

		if (item.getMimeType().equals(SurespotConstants.MimeTypes.TEXT) || deleted) {
			chatMessageViewHolder.tvText.setVisibility(View.VISIBLE);
			chatMessageViewHolder.imageView.setVisibility(View.GONE);
			chatMessageViewHolder.imageView.clearAnimation();
			chatMessageViewHolder.imageView.setImageBitmap(null);
			if (item.getPlainData() != null) {
				chatMessageViewHolder.tvText.clearAnimation();
				chatMessageViewHolder.tvText.setText(item.getPlainData());
			}
			else {
				chatMessageViewHolder.tvText.setText("");
				MessageDecryptor.decrypt(chatMessageViewHolder.tvText, item);
			}
			chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
			chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
		}
		else {
			chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvText.clearAnimation();
			chatMessageViewHolder.tvText.setVisibility(View.GONE);
			chatMessageViewHolder.tvText.setText("");
			if (!TextUtils.isEmpty(item.getData())) {
				ImageDownloader.download(chatMessageViewHolder.imageView, item);
			}

			if (item.isShareable()) {
				chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
				chatMessageViewHolder.ivShareable.setVisibility(View.VISIBLE);
			}
			else {
				chatMessageViewHolder.ivNotShareable.setVisibility(View.VISIBLE);
				chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
			}
		}

		if (type == TYPE_US) {
			chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
			chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
		}

		if (mDebugMode) {
			chatMessageViewHolder.tvId.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvToVersion.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvFromVersion.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvIv.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvData.setVisibility(View.VISIBLE);
			chatMessageViewHolder.tvMimeType.setVisibility(View.VISIBLE);

			chatMessageViewHolder.tvId.setText("id: " + item.getId());
			chatMessageViewHolder.tvToVersion.setText("toVersion: " + item.getToVersion());
			chatMessageViewHolder.tvFromVersion.setText("fromVersion: " + item.getFromVersion());
			chatMessageViewHolder.tvIv.setText("iv: " + item.getIv());
			chatMessageViewHolder.tvData.setText("data: " + item.getData());
			chatMessageViewHolder.tvMimeType.setText("mimeType: " + item.getMimeType());
		}

		return convertView;
	}

	public static class ChatMessageViewHolder {
		public TextView tvText;
		public TextView tvUser;
		public View vMessageSending;
		public View vMessageSent;
		public ImageView imageView;
		public TextView tvTime;
		public TextView tvId;
		public TextView tvToVersion;
		public TextView tvFromVersion;
		public TextView tvIv;
		public TextView tvData;
		public TextView tvMimeType;
		public ImageView ivShareable;
		public ImageView ivNotShareable;

	}

	public synchronized boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort, boolean notify)
			throws SurespotMessageSequenceException {
		boolean added = false;
		added = addOrUpdateMessage(message, checkSequence, sort);
		if (notify) {
			notifyDataSetChanged();
		}
		return added;

	}

	public synchronized void insertMessage(SurespotMessage message, boolean notify) {

		insertMessage(message);
		if (notify) {
			notifyDataSetChanged();
		}

	}

	public synchronized SurespotMessage deleteMessageByIv(String iv) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			message = iterator.next();

			if (message.getIv().equals(iv)) {
				iterator.remove();
				notifyDataSetChanged();
				return message;
			}
		}

		return null;
	}

	public synchronized SurespotMessage deleteMessageById(Integer id) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious();) {
			message = iterator.previous();

			Integer localId = message.getId();
			if (localId != null && localId.equals(id)) {
				SurespotLog.v(TAG, "deleting message");
				iterator.remove();
				notifyDataSetChanged();
				return message;
			}
		}

		return null;
	}

	public SurespotMessage getMessageById(Integer id) {
		SurespotMessage message = null;
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			message = iterator.next();

			Integer localId = message.getId();
			if (localId != null && localId.equals(id)) {
				// SurespotLog.v(TAG, "deleting message");
				// message.setCipherData("deleted");
				// if (message.getTo().equals(IdentityController.getLoggedInUser())) {
				// message.setDeletedTo(true);
				// }

				return message;
			}
		}
		// notifyDataSetChanged();
		return null;
	}

	public void sort() {
		Collections.sort(mMessages);

	}

	public synchronized void deleteBeforeAndInclusiveOf(int id, boolean myMessages) {
		for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext();) {
			SurespotMessage message = iterator.next();
			boolean myMessage = message.getFrom().equals(IdentityController.getLoggedInUser());

			if (message.getId() <= id && (myMessages || !myMessage)) {
				iterator.remove();
			}
		}
	}

}
