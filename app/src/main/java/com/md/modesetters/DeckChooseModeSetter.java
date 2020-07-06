package com.md.modesetters;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.md.CategorySingleton;
import com.md.DbNoteEditor;
import com.md.ModeHandler;
import com.md.R;
import com.md.RevisionQueue;
import com.md.modesetters.deckchoose.DeckDeleter;
import com.md.modesetters.deckchoose.DeckNameUpdater;
import com.md.modesetters.deckchoose.InsertNewHandler;
import com.md.provider.Deck;
import com.md.utils.ToastSingleton;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;

public class DeckChooseModeSetter extends ModeSetter {

	private static DeckChooseModeSetter instance = null;
	private int mTotalNotes;

	protected DeckChooseModeSetter() {
		deckInfoMap = new LinkedHashMap<Integer, DeckInfo>();
	}

	public static DeckChooseModeSetter getInstance() {
		if (instance == null) {
			instance = new DeckChooseModeSetter();
		}
		return instance;
	}

	private Activity memoryDroid;

	public void setUp(Activity memoryDroid,
			ModeHandler modeHand) {
		parentSetup(memoryDroid, modeHand);
		this.memoryDroid = memoryDroid;
	}

	public void setupModeImpl(final Activity context) {
		commonSetup(context, R.layout.deckchoosetemp);
		setupCreateMode();
	}

	private ListView mListView;
	private Vector<Deck> mAdapterDecks = new Vector<Deck>();
	private HashMap<Integer, DeckInfo> deckInfoMap;
	private boolean loadComplete;
	private ProgressBar progressBar;

	public void setupCreateMode() {

		loadComplete = false;

		Button insertButton = (Button) memoryDroid.findViewById(R.id.addNew);

		insertButton.setOnClickListener(new InsertNewHandler(memoryDroid,
				instance));

		mAdapterDecks.clear();

		Vector<Deck> queryDeck = DbNoteEditor.getInstance().queryDeck();

		for (Deck deck : queryDeck) {
			addDeck(deck.getName(), deck.getId());
		}

		setListView((ListView) memoryDroid.findViewById(R.id.ListView01));

		progressBar = (ProgressBar) memoryDroid.findViewById(R.id.progressBar);

		progressBar.setMax(mAdapterDecks.size());

		progressBar.setProgress(0);

		progressBar.setVisibility(View.VISIBLE);

		ArrayAdapter<Deck> arrayAdapter = new ArrayAdapter<Deck>(memoryDroid,
				android.R.layout.simple_list_item_1, mAdapterDecks);

		// By using setAdpater method in listview we an add string array in
		// list.
		getListView().setAdapter(arrayAdapter);

		OnItemClickListener onItemClickListener = new OnItemClickListenerImplementation(
				memoryDroid);
		getListView().setOnItemClickListener(onItemClickListener);
		OnItemLongClickListener onItemLongClickListener = new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> av, final View v,
										   int index, long arg) {
				if (!loadComplete) {
					return true;
				}
				final DeckInfo deckInfo = deckInfoMap.get((int) arg);
				AlertDialog.Builder alert = new AlertDialog.Builder(memoryDroid);
				alert.setTitle("Choose action or press off screen");
				alert.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						new DeckNameUpdater(memoryDroid, deckInfo, instance).onClick(v);
					}
				});

				alert.setNegativeButton("Delete", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						new DeckDeleter(memoryDroid, deckInfo, instance).onClick(v);
					}
				});
				alert.show();
				return true;
			}
		};

		getListView().setOnItemLongClickListener(onItemLongClickListener);

		DeckPopulator deckPopulator = new DeckPopulator(this);

		deckPopulator.execute(this);
	}

	private final class OnItemClickListenerImplementation implements
			OnItemClickListener {

		private OnItemClickListenerImplementation(Activity memoryDroid) {

		}

		@Override
		public void onItemClick(AdapterView<?> av, View v, int index, long arg) {
			if (loadComplete) {
				DeckInfo deckInfo = deckInfoMap.get(Integer.valueOf((int) arg));
				loadDeck(deckInfo);
			}
		}
	}

	public class DeckPopulator extends
			AsyncTask<DeckChooseModeSetter, DeckInfo, DeckInfo> {

		private DeckChooseModeSetter dcChooseModeSetter;

		public DeckPopulator(DeckChooseModeSetter dcChooseModeSetter) {
			this.dcChooseModeSetter = dcChooseModeSetter;

		}

		protected void onPostExecute(DeckInfo result) {
			if (modeHand.whoseOnTop() == dcChooseModeSetter) {
				dcChooseModeSetter.onComplete();
			}
		}

		public void publishProgessVisible(DeckInfo state) {
			// Only do stuff if I'm still in control.
			if (modeHand.whoseOnTop() == dcChooseModeSetter) {
				publishProgress(state);
			}
		}

		protected void onProgressUpdate(DeckInfo... state) {
			dcChooseModeSetter.setState(state[0]);
		}

		@Override
		protected DeckInfo doInBackground(DeckChooseModeSetter... params) {

			mTotalNotes = 0;
			int childCount = mAdapterDecks.size();

			for (int idx = 0; idx < childCount; idx++) {
				Deck elementAt = mAdapterDecks.elementAt(idx);

				// Stop if we aren't loaded anymore.
				if (modeHand.whoseOnTop() != dcChooseModeSetter) {
					break;
				}

				RevisionQueue revisionQueue = new RevisionQueue();
				revisionQueue.populate(DbNoteEditor.getInstance(),
						elementAt.getId());

				// Stop if we aren't loaded anymore. We want this before
				// and after the query
				if (modeHand.whoseOnTop() != dcChooseModeSetter) {
					break;
				}

				// TODO why is the counting when we have the size.
				int deckCount = DbNoteEditor.getInstance().getDeckCount(
						elementAt.getId());

				DeckInfo deckInfo = new DeckInfo(elementAt, revisionQueue,
						deckCount);

				publishProgessVisible(deckInfo);
			}
			return null;
		}
	}

	private void addDeck(String name, int id) {
		Deck deck = new Deck(id, name);
		mAdapterDecks.add(deck);
	}

	public void onComplete() {
		TextView loadingOrSelect = (TextView) memoryDroid
				.findViewById(R.id.loadingOrSelect);

		loadingOrSelect.setText("Press or Long Press a Deck");

		loadComplete = true;

		ToastSingleton.getInstance().msgCommon(mTotalNotes + " notes!", 0);
	}

	public void setState(DeckInfo state) {
		progressBar.setProgress(progressBar.getProgress() + 1);

		for (int idx = 0; idx < mAdapterDecks.size(); idx++) {
			Deck elementAt = mAdapterDecks.elementAt(idx);
			if (elementAt.getId() == state.getCategory()) {
				elementAt.setSize(state.getDeckCount());
				elementAt.setTodayReview(state.getRevisionQueue().getSize());
				deckInfoMap.put(Integer.valueOf(idx), state);
				// TODO(jrochest) Why is the necessary. It only needs it when returning from
				// a different mode, but it is not needed initially.
				if (mListView.getChildCount() - 1 >= idx) {
					((TextView)mListView.getChildAt(idx)).setText(state.getDeck().toString());
				}
			}
		}
	}

	public void setListView(ListView listView) {
		this.mListView = listView;
	}

	public ListView getListView() {
		return mListView;
	}

	public void loadDeck(DeckInfo deckInfo) {

		if (deckInfo != null) {
			CategorySingleton.getInstance().setDeckInfo(deckInfo);

			RevisionQueue.getInstance().makeThisLookLikeThat(
					deckInfo.getRevisionQueue());
		}

		LearningModeSetter.getInstance().setupMode(memoryDroid);
	}

    public DeckInfo getDefaultDeck() {
        if (!deckInfoMap.isEmpty()) {
            // Return the first and the default.
            return deckInfoMap.entrySet().iterator().next().getValue();
        }
        return null;
    }

}