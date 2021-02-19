package hbs.app.securemail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RecyclerViewAdapterMessagesRow extends RecyclerView.Adapter<RecyclerViewAdapterMessagesRow.ViewHolder> {
    // listener
    public interface ItemClickListener{
        void onItemClick(View view, int position);
    }

    // variables
    private LayoutInflater m_inflater;
    private List<String> m_data;
    private ItemClickListener m_item_click_listener;

    // methods
    public RecyclerViewAdapterMessagesRow(Context context, List<String> data){
        this.m_inflater = LayoutInflater.from(context);
        this.m_data = data;
    }

    @NonNull
    @Override
    public RecyclerViewAdapterMessagesRow.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = this.m_inflater.inflate(R.layout.recyclerview_messages_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewAdapterMessagesRow.ViewHolder holder, int position) {
        String s = this.m_data.get(position);
        holder.setSubject(s);
    }

    @Override
    public int getItemCount() {
        return this.m_data.size();
    }

    public void setClickListener(ItemClickListener listener){
        this.m_item_click_listener = listener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        // variables
        private TextView m_textview_subject;

        // methods
        public ViewHolder(View view){
            super(view);
            this.m_textview_subject = view.findViewById(R.id.recyclerview_messages_row_textview_subject);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (RecyclerViewAdapterMessagesRow.this.m_item_click_listener != null){
                RecyclerViewAdapterMessagesRow.this.m_item_click_listener.onItemClick(view, this.getAdapterPosition());
            }
        }

        public void setSubject(String subject){
            this.m_textview_subject.setText(subject);
        }
    }
}
