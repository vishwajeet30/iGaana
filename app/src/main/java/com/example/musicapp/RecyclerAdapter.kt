package com.example.musicapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView

class RecylerAdapter : RecyclerView.Adapter<RecylerAdapter.ViewHolder>() {


    private var onSongClicked: SongClicked? = null
    private var selecitonModeActive = false

    private var songsList = mutableListOf<Song>()
    private var selectedSongs = mutableListOf<Song>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecylerAdapter.ViewHolder {
        return ViewHolder(LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.track_item,viewGroup,false))
    }

    override fun getItemCount(): Int {
        return songsList.size
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val song = songsList[position]
        viewHolder.bind(song,position)
        viewHolder.mainItem.isSelected = selectedSongs.contains(song)



        viewHolder.mainItem.setOnClickListener {
            if (!selecitonModeActive){
                onSongClicked?.onSongClicked(song)
            }
        }


    }


    fun addSongs(song: MutableList<Song>){

        songsList.clear()
        songsList.addAll(song)
        notifyDataSetChanged()
    }




    fun setOnSongClicked(songClick:SongClicked){
        this.onSongClicked = songClick
    }

    interface SongClicked{
        fun onSongClicked(song:Song)
    }






    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){

        private val title : TextView = itemView.findViewById(R.id.textViewSongTtitle)
        private val artist : TextView = itemView.findViewById(R.id.textViewArtistName)
        val mainItem: ConstraintLayout = itemView.findViewById(R.id.mainConstraint)
        private var position: Int? = null

        fun bind(song: Song,pos: Int){

            title.text = song.title
            artist.text = song.artistName
            position = pos

        }

    }


}