package com.flawiddsouza.writer

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.TextView

class CategoryCursorAdapter(context: Context, cursor: Cursor) : CursorAdapter(context, cursor, 0) {

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.list_item_1, parent, false)
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val text1 = view.findViewById<View>(R.id.text1) as TextView
        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
        text1.text = name
    }
}
