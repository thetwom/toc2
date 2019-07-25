package toc2.toc2;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

class SaveDataFragment extends Fragment {

    private RecyclerView savedItems = null;
    private RecyclerView.LayoutManager savedItemsManager;
    private final SavedItemDatabase savedItemsAdapter = new SavedItemDatabase();
    private Rect backgroundArea;

    private int lastRemovedItemIndex = -1;
    private SavedItemDatabase.SavedItem lastRemovedItem = null;

    private int deleteTextSize;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        savedItemsManager = new LinearLayoutManager(getActivity());
        savedItemsAdapter.loadData(getActivity());
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
//        super.onPrepareOptionsMenu(menu);
        MenuItem settingsItem = menu.findItem(R.id.action_properties);
        if(settingsItem != null)
            settingsItem.setVisible(false);

        MenuItem loadDataItem = menu.findItem(R.id.action_load);
        if(loadDataItem != null)
            loadDataItem.setVisible(false);

        MenuItem saveDataItem = menu.findItem(R.id.action_save);
        if(saveDataItem != null)
            saveDataItem.setVisible(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_save_data, container, false);

        deleteTextSize = Utilities.sp_to_px(18);

        savedItems = view.findViewById(R.id.savedItems);
        savedItems.setHasFixedSize(true);
        savedItems.setLayoutManager(savedItemsManager);
        savedItems.setAdapter(savedItemsAdapter);

        backgroundArea = new Rect();

        final SavedItemAttributes savedItemAttributes = view.findViewById(R.id.saved_item_attributes);
        assert savedItemAttributes != null;

        ItemTouchHelper.SimpleCallback simpleTouchHelper = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            Drawable background;
            Paint undoPaint;
//            Drawable xMark;
//            int xMarkMargin;
            boolean initialized = false;

            private void init() {
                if(initialized)
                    return;

                background = new ColorDrawable(savedItemAttributes.deleteColor);
                undoPaint = new Paint();
                undoPaint.setTextSize(deleteTextSize);
                undoPaint.setAntiAlias(true);
                undoPaint.setStyle(Paint.Style.FILL);
                undoPaint.setTextAlign(Paint.Align.RIGHT);
                undoPaint.setColor(savedItemAttributes.onDeleteColor);
//                xMark = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_clear_24dp);
//                xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
//                xMarkMargin = (int) MainActivity.this.getResources().getDimension(R.dimen.ic_clear_margin);
                initialized = true;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                Log.v("Metronome", "SaveDataFragment:onSwiped " + viewHolder.getAdapterPosition());

                lastRemovedItemIndex = viewHolder.getAdapterPosition();
                lastRemovedItem = savedItemsAdapter.remove(lastRemovedItemIndex);
                CoordinatorLayout view = (CoordinatorLayout) getView();
                assert view != null;

                Snackbar.make(view, getString(R.string.item_deleted), Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, new View.OnClickListener(){
                            @Override
                            public void onClick(View v) {
                                if(lastRemovedItem != null) {
                                    savedItemsAdapter.addItem(getActivity(), lastRemovedItem, lastRemovedItemIndex);
                                    lastRemovedItem = null;
                                    lastRemovedItemIndex = -1;
                                }
                            }
                        }).show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

                init();
                View itemView = viewHolder.itemView;

                // not sure why, but this method get's called for viewholder that are already swiped away
                if (viewHolder.getAdapterPosition() == -1) {
                    // not interested in those
                    return;
                }

                backgroundArea.set(itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom());
                background.setBounds(backgroundArea);
                background.draw(c);

                c.drawText(getString(R.string.delete), itemView.getRight()-itemView.getPaddingRight()-Utilities.dp_to_px(8),
                        (itemView.getTop()+itemView.getBottom() + deleteTextSize)/2.0f, undoPaint);

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper touchHelper = new ItemTouchHelper(simpleTouchHelper);
        touchHelper.attachToRecyclerView(savedItems);

        return view;
    }

    @Override
    public void onDetach() {
        savedItemsAdapter.saveData(getActivity());
        super.onDetach();
    }

    void saveItem(FragmentActivity activity, SavedItemDatabase.SavedItem item)
    {
        assert savedItemsAdapter != null;
        savedItemsAdapter.addItem(activity, item);
    }


    public void setOnItemClickedListener(SavedItemDatabase.OnItemClickedListener onItemClickedListener) {
        savedItemsAdapter.setOnItemClickedListener(onItemClickedListener);
    }
}
