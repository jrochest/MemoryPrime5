package com.md.modesetters

import android.annotation.SuppressLint
import android.app.Activity
import android.os.AsyncTask
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.app.AlertDialog
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueue
import com.md.modesetters.DeckItemPopulator.populate
import com.md.modesetters.deckchoose.DeckDeleter
import com.md.modesetters.deckchoose.DeckNameUpdater
import com.md.modesetters.deckchoose.InsertNewHandler
import com.md.provider.Deck
import com.md.utils.ToastSingleton
import java.util.*

@SuppressLint("StaticFieldLeak")
object DeckChooseModeSetter : ModeSetter() {
    private var mTotalNotes = 0
    @SuppressLint("StaticFieldLeak")
    private var memoryDroid: Activity? = null
    fun setUp(memoryDroid: Activity?,
              modeHand: ModeHandler?) {
        parentSetup(memoryDroid, modeHand)
        this.memoryDroid = memoryDroid
    }

    override fun switchModeImpl(context: Activity) {
        commonSetup(context, R.layout.deckchoosetemp)
        setupCreateMode()
    }

    var listView: ListView? = null
    private val mAdapterDecks = Vector<Deck>()
    private var deckInfoMap: HashMap<Int, DeckInfo> = LinkedHashMap()
    private var loadComplete = false
    private var progressBar: ProgressBar? = null
    fun setupCreateMode() {
        loadComplete = false
        val insertButton = memoryDroid!!.findViewById<View>(R.id.addNew) as Button
        insertButton.setOnClickListener(InsertNewHandler(memoryDroid,
                this))
        mAdapterDecks.clear()
        val queryDeck = DbNoteEditor.instance!!.queryDeck()
        for (deck in queryDeck) {
            addDeck(deck.name, deck.id)
        }
        listView = memoryDroid!!.findViewById<View>(R.id.ListView01) as ListView
        progressBar = memoryDroid!!.findViewById<View>(R.id.progressBar) as ProgressBar
        progressBar!!.max = mAdapterDecks.size
        progressBar!!.progress = 0
        progressBar!!.visibility = View.VISIBLE

        // By using setAdpater method in listview we an add string array in
        // list.
        listView!!.adapter = DeckAdapter(mAdapterDecks, mActivity!!)
        val onItemClickListener: OnItemClickListener = OnItemClickListenerImplementation(
                memoryDroid)
        listView!!.onItemClickListener = onItemClickListener

        val deckPopulator = DeckPopulator(this)
        deckPopulator.execute(this)
    }

    private class DeckAdapter(private val decks: List<Deck>, private val context: Activity) : BaseAdapter() {
        override fun getCount(): Int {
            return decks.size
        }

        override fun getItem(position: Int): Any {
            return decks[position]
        }

        override fun getItemId(position: Int): Long {
            return decks[position].id.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: context.layoutInflater.inflate(R.layout.deck_item_for_list, parent, false)

            val deck = decks[position]
            val deckInfo = deckInfoMap[deck.id]
            val textView = (view.findViewById<View>(R.id.deck_name) as TextView)
            if (deckInfo != null) {
                populate(position, view, deckInfo)
            } else {
                (view.findViewById<View>(R.id.deck_name) as TextView).text = deck.name
            }
            textView.setOnLongClickListener { v ->
                val info = deckInfoMap[deck.id] ?: return@setOnLongClickListener true
                val alert = AlertDialog.Builder(memoryDroid!!)
                alert.setTitle("Choose action or press off screen")
                alert.setPositiveButton("Rename") { _, _ -> DeckNameUpdater(memoryDroid, info, getInstance()).onClick(v) }
                alert.setNegativeButton("Delete") { _, _ -> DeckDeleter(memoryDroid, info, getInstance()).onClick(v) }
                alert.show()
                true
            }
            view.findViewById<View>(R.id.new_note_button).setOnClickListener { v: View? ->
                val info = deckInfoMap[deck.id] ?: return@setOnClickListener
                loadDeck(info)
            }
            view.findViewById<View>(R.id.learn_button).setOnClickListener { v: View? ->
                val info = deckInfoMap[deck.id] ?: return@setOnClickListener
                loadDeck(info)
            }
            return view
        }
    }

    private  class OnItemClickListenerImplementation(memoryDroid: Activity?) : OnItemClickListener {
        override fun onItemClick(av: AdapterView<*>?, v: View, index: Int, arg: Long) {
            if (loadComplete) {
                val deckInfo = deckInfoMap[Integer.valueOf(arg.toInt())]
                loadDeck(deckInfo)
            }
        }
    }

    class DeckPopulator(private val dcChooseModeSetter: DeckChooseModeSetter) : AsyncTask<DeckChooseModeSetter?, DeckInfo?, DeckInfo?>() {
        override fun onPostExecute(result: DeckInfo?) {
            if (modeHand!!.whoseOnTop() === dcChooseModeSetter) {
                dcChooseModeSetter.onComplete()
            }
        }

        fun publishProgessVisible(state: DeckInfo?) {
            // Only do stuff if I'm still in control.
            if (modeHand!!.whoseOnTop() === dcChooseModeSetter) {
                publishProgress(state)
            }
        }

        protected override fun onProgressUpdate(vararg state: DeckInfo?) {
            val stateFirst = state[0] ?: return

            dcChooseModeSetter.setState(stateFirst)
        }

        override fun doInBackground(vararg params: DeckChooseModeSetter?): DeckInfo? {
            mTotalNotes = 0
            val childCount = mAdapterDecks.size
            for (idx in 0 until childCount) {
                val elementAt = mAdapterDecks.elementAt(idx)

                // Stop if we aren't loaded anymore.
                if (modeHand!!.whoseOnTop() !== dcChooseModeSetter) {
                    break
                }
                val revisionQueue = RevisionQueue()
                revisionQueue.populate(DbNoteEditor.instance!!, elementAt.id)

                // Stop if we aren't loaded anymore. We want this before
                // and after the query
                if (modeHand!!.whoseOnTop() !== dcChooseModeSetter) {
                    break
                }

                // TODO why is the counting when we have the size.
                val deckCount = DbNoteEditor.instance!!.getDeckCount(
                        elementAt.id)
                val deckInfo = DeckInfo(elementAt, revisionQueue,
                        deckCount)
                publishProgessVisible(deckInfo)
            }
            return null
        }

    }

    private fun addDeck(name: String, id: Int) {
        val deck = Deck(id, name)
        mAdapterDecks.add(deck)
    }

    fun onComplete() {
        val loadingOrSelect = memoryDroid?.findViewById<View>(R.id.loadingOrSelect) as TextView
        if (loadingOrSelect != null) {
            loadingOrSelect.text = "Press or Long Press a Deck"
        }
        loadComplete = true
        ToastSingleton.getInstance().msgCommon("$mTotalNotes notes!", 0f)
    }

    fun setState(state: DeckInfo) {
        progressBar!!.progress = progressBar!!.progress + 1
        for (idx in mAdapterDecks.indices) {
            val elementAt = mAdapterDecks.elementAt(idx)
            if (elementAt.id == state.category) {
                elementAt.setSize(state.deckCount)
                elementAt.setTodayReview(state.revisionQueue.getSize())
                deckInfoMap[Integer.valueOf(idx)] = state
                // TODO(jrochest) Why is the necessary. It only needs it when returning from
                // a different mode, but it is not needed initially.
                if (listView!!.childCount - 1 >= idx) {
                    populate(idx, listView!!.getChildAt(idx), state)
                }
            }
        }
    }

    fun loadDeck(deckInfo: DeckInfo?) {
        if (deckInfo != null) {
            CategorySingleton.getInstance().setDeckInfo(deckInfo)
            currentDeckReviewQueue = deckInfo.revisionQueue
        }
        LearningModeSetter.instance.switchMode(memoryDroid!!)
    }

    // Return the first and the default.
    val defaultDeck: DeckInfo?
        get() = if (!deckInfoMap.isEmpty()) {
            // Return the first and the default.
            deckInfoMap.entries.iterator().next().value
        } else null
    val nextDeckWithItems: DeckInfo?
        get() {
            if (deckInfoMap.isEmpty()) {
                return null
            }
            for ((_, value) in deckInfoMap) {
                if (value.name.contains("inactive")) {
                    continue
                }
                if (value.revisionQueue.getSize() > 0) {
                    return value
                }
            }
            return null
        }

        fun getInstance() = this


}