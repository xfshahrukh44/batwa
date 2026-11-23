package com.example.dailybalance;

import android.app.Activity;
import android.content.res.ColorStateList;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout root;
    private String currentScreen = "home";
    private int selectedHour = 0, selectedMinute = 0;
    private long rangeStart, rangeEnd;
    private String homeSearchQuery = "";
    private GoogleSignInClient googleSignInClient;
    private static final int RC_GOOGLE_SIGN_IN = 9107;
    private int ink=0xFF18352B, muted=0xFF718078, green=0xFF26734D, bg=0xFFF4F8F4, red=0xFFBA4A45, line=0xFFE1EAE2, paleGreen=0xFFE4F2E7;
    private boolean darkMode;

    @Override public void onCreate(Bundle b) {
        boolean useDarkTheme=getSharedPreferences("daily_balance_store",MODE_PRIVATE).getBoolean("dark_mode",false);
        setTheme(useDarkTheme?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(b);
        applyThemeColors();
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail().requestIdToken(getString(R.string.default_web_client_id)).build();
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
        updateSystemBars();
        Store.migrateOpeningBalance(this);
        if (!Store.isSetup(this)) showOpeningSetup(); else { initRange(); maybeCatchUp(); showMain(); }
    }
    private void applyThemeColors(){
        darkMode=Store.prefs(this).getBoolean("dark_mode",false);
        if(darkMode){ink=0xFFE5EFE8;muted=0xFFA4B2A9;green=0xFF8DCAA4;bg=0xFF101713;red=0xFFF28B82;line=0xFF2E4036;paleGreen=0xFF1D3828;}
        else{ink=0xFF18352B;muted=0xFF718078;green=0xFF26734D;bg=0xFFF4F8F4;red=0xFFBA4A45;line=0xFFE1EAE2;paleGreen=0xFFE4F2E7;}
    }
    private void updateSystemBars(){
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(darkMode?0:(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
    }
    private void toggleDarkMode(){
        Store.prefs(this).edit().putBoolean("dark_mode",!darkMode).apply();
        recreate();
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private Button button(String s){MaterialButton b=new MaterialButton(this);b.setText(s);b.setAllCaps(false);b.setCornerRadius(dp(14));b.setInsetTop(0);b.setInsetBottom(0);b.setRippleColor(ColorStateList.valueOf(0x3326734D));return b;}
    private void safeInsets(LinearLayout target,int horizontal,int top,int bottom){
        target.setOnApplyWindowInsetsListener((v,insets)->{
            int t=0,b=0;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.statusBars()|android.view.WindowInsets.Type.navigationBars());t=bars.top;b=bars.bottom;}
            else {t=dp(24);b=dp(24);}
            v.setPadding(dp(horizontal),t+dp(top),dp(horizontal),b+dp(bottom));
            return insets;
        });
        target.requestApplyInsets();
    }
    private MaterialCardView card(){MaterialCardView c=new MaterialCardView(this);c.setRadius(dp(20));c.setCardElevation(dp(1));c.setStrokeWidth(dp(1));c.setStrokeColor(line);c.setCardBackgroundColor(darkMode?0xFF1A241E:0xFFFFFFFF);return c;}
    private EditText field(String hint,boolean decimal){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setInputType(decimal?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);return e;}
    private Double number(String s){try{return Double.parseDouble(s.trim().replace(",",""));}catch(Exception e){return null;}}
    private void initRange(){Calendar c=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,1);startDay(c);rangeStart=c.getTimeInMillis();c=Calendar.getInstance();endDay(c);rangeEnd=c.getTimeInMillis();}
    private void startDay(Calendar c){c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
    private void endDay(Calendar c){c.set(Calendar.HOUR_OF_DAY,23);c.set(Calendar.MINUTE,59);c.set(Calendar.SECOND,59);c.set(Calendar.MILLISECOND,999);}
    private String shortDate(long t){return new SimpleDateFormat("dd-MM-yy",Locale.US).format(new Date(t));}

    private void showOpeningSetup(){
        LinearLayout box=column();box.setGravity(Gravity.CENTER);safeInsets(box,28,24,24);box.setBackgroundColor(bg);
        TextView title=text("Batwa",30,ink);title.setGravity(Gravity.CENTER);box.addView(title);
        TextView sub=text("Set your starting balance",15,muted);sub.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(10);box.addView(sub,sp);
        EditText amount=field("Starting amount in PKR",true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(28);box.addView(amount,ap);
        Button start=button("Get started");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(16);box.addView(start,bp);
        start.setOnClickListener(v->{Double n=number(amount.getText().toString());if(n==null||n<0){amount.setError("Enter a valid amount");return;}Store.setSetup(this,n);initRange();showMain();});setContentView(box);
    }
    private void maybeCatchUp(){
        if(!Store.dailyEnabled(this))return;
        Calendar now=Calendar.getInstance(),due=(Calendar)now.clone();due.set(Calendar.HOUR_OF_DAY,Store.dailyHour(this));due.set(Calendar.MINUTE,Store.dailyMinute(this));due.set(Calendar.SECOND,0);due.set(Calendar.MILLISECOND,0);
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now.getTime());
        if(now.after(due)&&!today.equals(Store.lastCreditDate(this))){Store.addTransaction(this,"Daily income",Store.dailyAmount(this),System.currentTimeMillis());Store.setLastCreditDate(this,today);}Alarm.schedule(this);
    }
    private void pageBase(String screen,String title){currentScreen=screen;root=column();root.setBackgroundColor(bg);safeInsets(root,20,14,8);TextView heading=text(title,25,ink);heading.setTypeface(null,1);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(8);root.addView(heading,hp);}
    private void addNav(){
        BottomNavigationView nav=new BottomNavigationView(this);
        nav.setBackgroundColor(darkMode?0xFF1A241E:0xFFFFFFFF);
        nav.setElevation(dp(8));
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.getMenu().add(0,1,0,"Home").setIcon(R.drawable.ic_home);
        nav.getMenu().add(0,2,1,"Daily").setIcon(R.drawable.ic_schedule);
        nav.getMenu().add(0,3,2,"Spending").setIcon(R.drawable.ic_chart);
        nav.getMenu().add(0,4,3,"Tags").setIcon(R.drawable.ic_tag);
        nav.setSelectedItemId(currentScreen.equals("home")?1:currentScreen.equals("daily")?2:currentScreen.equals("spending")?3:4);
        nav.setOnItemSelectedListener(item->{switch(item.getItemId()){case 1:showMain();return true;case 2:showDaily();return true;case 3:showSpending();return true;case 4:showTags();return true;}return false;});
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);root.addView(nav,np);
    }
    private void showMain(){
        currentScreen="home";root=column();root.setBackgroundColor(bg);safeInsets(root,20,14,8);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        FirebaseAuth auth=FirebaseAuth.getInstance();
        String accountLabel=auth.getCurrentUser()==null?"Sign in":"☁ "+(auth.getCurrentUser().getEmail()==null?"Account":auth.getCurrentUser().getEmail());
        Button account=button(accountLabel);account.setTextSize(11);top.addView(account,new LinearLayout.LayoutParams(0,dp(42),1));account.setOnClickListener(v->showAccountDialog());
        View topSpace=new View(this);top.addView(topSpace,new LinearLayout.LayoutParams(dp(6),1));
        Button theme=button(darkMode?"☀":"☾");theme.setContentDescription("Toggle dark mode");theme.setMinWidth(dp(42));theme.setMinimumWidth(dp(42));top.addView(theme);theme.setOnClickListener(v->toggleDarkMode());
        View themeSpace=new View(this);top.addView(themeSpace,new LinearLayout.LayoutParams(dp(6),1));
        Button add=button("＋  Add transaction");add.setTextColor(0xFFFFFFFF);top.addView(add);root.addView(top,new LinearLayout.LayoutParams(-1,-2));add.setOnClickListener(v->showExpenseDialog());
        syncIfSignedIn();
        MaterialCardView balanceCard=card();balanceCard.setCardBackgroundColor(paleGreen);LinearLayout balanceBox=column();pad(balanceBox,18,18,18,18);
        TextView label=text("CURRENT BALANCE",11,muted);label.setGravity(Gravity.CENTER);balanceBox.addView(label);
        TextView balance=text(Store.money(Store.balance(this)),30,green);balance.setGravity(Gravity.CENTER);balance.setTypeface(null,1);LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,-2);blp.topMargin=dp(5);balanceBox.addView(balance,blp);
        TextView editHint=text("Tap to edit starting balance",11,muted);editHint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ehp=new LinearLayout.LayoutParams(-1,-2);ehp.topMargin=dp(5);balanceBox.addView(editHint,ehp);
        balanceCard.addView(balanceBox);LinearLayout.LayoutParams bcp=new LinearLayout.LayoutParams(-1,-2);bcp.topMargin=dp(12);bcp.bottomMargin=dp(18);root.addView(balanceCard,bcp);
        balanceCard.setOnClickListener(v->showOpeningBalanceDialog());balanceCard.setClickable(true);balanceCard.setFocusable(true);balanceCard.setForeground(getDrawable(android.R.drawable.list_selector_background));
        LinearLayout txHeading=row();
        TextView txTitle=text("Transactions",18,ink);txTitle.setTypeface(null,1);
        txHeading.addView(txTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button search=button("⌕");
        search.setTextSize(22);search.setContentDescription("Search transactions");
        search.setMinWidth(dp(44));search.setMinimumWidth(dp(44));
        txHeading.addView(search);
        search.setOnClickListener(v->showTransactionSearch());
        root.addView(txHeading);
        JSONArray arr=Store.transactions(this);
        RecyclerView transactionList=new RecyclerView(this);
        transactionList.setLayoutManager(new LinearLayoutManager(this));
        transactionList.setItemViewCacheSize(12);
        LinearLayout.LayoutParams scp=new LinearLayout.LayoutParams(-1,0,1);scp.topMargin=dp(5);root.addView(transactionList,scp);
        TransactionListAdapter transactionAdapter=new TransactionListAdapter(arr,homeSearchQuery);
        transactionList.setAdapter(transactionAdapter);
        transactionList.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView view,int dx,int dy){
                if(dy>0&&!view.canScrollVertically(1))transactionAdapter.loadOlder();
            }
        });
        addNav();setContentView(root);
    }
    private void showOpeningBalanceDialog(){
        LinearLayout box=column();pad(box,20,4,20,0);EditText amount=field("Starting balance in PKR",true);amount.setText(String.valueOf(Store.openingBalance(this)));box.addView(amount);
        new MaterialAlertDialogBuilder(this).setTitle("Edit starting balance").setMessage("Changing this amount adjusts your current balance by the difference. Your expenses and daily credits remain unchanged.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{Double n=number(amount.getText().toString());if(n==null||n<0){Toast.makeText(this,"Enter a valid amount",Toast.LENGTH_SHORT).show();return;}Store.changeOpeningBalance(this,n);showMain();}).show();
    }
    private void showExpenseDialog(){
        LinearLayout box=column();pad(box,20,2,20,0);EditText amount=field("Amount (PKR)",true);EditText desc=field("Description",false);AutoCompleteTextView tag=new AutoCompleteTextView(this);tag.setSingleLine(true);tag.setHint("Tag (optional — creates new tags)");tag.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        JSONArray tags=Store.tags(this);String[] names=new String[tags.length()];for(int i=0;i<tags.length();i++)names[i]=tags.optJSONObject(i).optString("name");tag.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,names));
        CheckBox debit=new CheckBox(this);debit.setText("Debit (subtract from balance)");debit.setTextColor(ink);debit.setTextSize(14);debit.setButtonTintList(ColorStateList.valueOf(green));debit.setChecked(true);
        box.addView(amount);box.addView(desc);box.addView(tag);LinearLayout.LayoutParams debitParams=new LinearLayout.LayoutParams(-1,-2);debitParams.topMargin=dp(8);box.addView(debit,debitParams);
        new MaterialAlertDialogBuilder(this).setTitle("Add transaction").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{Double n=number(amount.getText().toString());String label=desc.getText().toString().trim(),tagName=tag.getText().toString().trim();if(n==null||n<=0||label.isEmpty()){Toast.makeText(this,"Enter an amount and description",Toast.LENGTH_SHORT).show();return;}if(!tagName.isEmpty()&&!Store.hasTag(this,tagName))Store.saveTag(this,tagName,"🏷️");double signedAmount=debit.isChecked()?-n:n;Store.addTransaction(this,label,signedAmount,System.currentTimeMillis(),tagName);showMain();}).show();
    }


    private void syncIfSignedIn(){
        if(FirebaseAuth.getInstance().getCurrentUser()!=null){
            SyncManager.sync(this).addOnFailureListener(error ->
                    Toast.makeText(this,"Sync paused: "+error.getMessage(),Toast.LENGTH_LONG).show());
        }
    }

    private void showAccountDialog(){
        if(FirebaseAuth.getInstance().getCurrentUser()==null){
            startActivityForResult(googleSignInClient.getSignInIntent(),RC_GOOGLE_SIGN_IN);
            return;
        }
        String email=FirebaseAuth.getInstance().getCurrentUser().getEmail();
        new MaterialAlertDialogBuilder(this).setTitle("Batwa cloud sync")
                .setMessage("Signed in as "+(email==null?"your Google account":email)+".\nYour transactions and tags sync through your private Firestore account.")
                .setPositiveButton("Sync now",(d,w)->SyncManager.sync(this)
                        .addOnSuccessListener(v->Toast.makeText(this,"Batwa is up to date",Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e->Toast.makeText(this,"Sync failed: "+e.getMessage(),Toast.LENGTH_LONG).show()))
                .setNeutralButton("Sign out",(d,w)->{
                    FirebaseAuth.getInstance().signOut();
                    googleSignInClient.signOut();
                    Toast.makeText(this,"Signed out. Local data remains on this device.",Toast.LENGTH_LONG).show();
                    showMain();
                }).setNegativeButton("Close",null).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=RC_GOOGLE_SIGN_IN)return;
        try{
            GoogleSignInAccount account=GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            if(account==null||account.getIdToken()==null)throw new IllegalStateException("Google did not return an ID token. Check the Firebase web OAuth client configuration.");
            com.google.firebase.auth.AuthCredential credential=GoogleAuthProvider.getCredential(account.getIdToken(),null);
            FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener(result->SyncManager.sync(this)
                            .addOnSuccessListener(v->{Toast.makeText(this,"Google account connected and Batwa synced",Toast.LENGTH_LONG).show();showMain();})
                            .addOnFailureListener(e->Toast.makeText(this,"Signed in, but sync failed: "+e.getMessage(),Toast.LENGTH_LONG).show()))
                    .addOnFailureListener(e->Toast.makeText(this,"Sign-in failed: "+e.getMessage(),Toast.LENGTH_LONG).show());
        }catch(ApiException e){
            Toast.makeText(this,"Google sign-in cancelled or failed: "+e.getStatusCode(),Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Toast.makeText(this,"Google sign-in failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }


    /** RecyclerView-backed transaction history: current month first, then older batches of 50. */
    private final class TransactionListAdapter extends RecyclerView.Adapter<TransactionListAdapter.Holder>{
        private final JSONArray source;
        private final String query;
        private final java.util.List<Integer> visible=new java.util.ArrayList<>();
        private int olderCursor;
        private boolean hasMore;
        private final long monthStart;
        TransactionListAdapter(JSONArray source,String query){
            this.source=source;this.query=query==null?"":query.trim().toLowerCase(Locale.ROOT);
            Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);startDay(month);monthStart=month.getTimeInMillis();
            olderCursor=source.length();hasMore=false;
            for(int i=0;i<source.length();i++){
                JSONObject item=source.optJSONObject(i);if(item==null)continue;
                if(item.optLong("time",0)<monthStart){olderCursor=i;hasMore=true;break;}
                if(matches(item))visible.add(i);
            }
            if(visible.isEmpty()&&hasMore)loadOlder();
        }
        private boolean matches(JSONObject item){
            return query.isEmpty()||item.optString("description","").toLowerCase(Locale.ROOT).contains(query)
                    ||item.optString("tag","").toLowerCase(Locale.ROOT).contains(query);
        }
        void loadOlder(){
            if(!hasMore)return;
            int added=0;
            while(olderCursor<source.length()&&added<50){
                int index=olderCursor++;JSONObject item=source.optJSONObject(index);
                if(item!=null&&matches(item)){visible.add(index);added++;}
            }
            hasMore=olderCursor<source.length();
            if(added>0)notifyItemRangeInserted(visible.size()-added,added);
        }
        @Override public int getItemViewType(int position){return visible.isEmpty()?1:0;}
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            if(type==1){TextView empty=text(source.length()==0?"No transactions yet":"No matching transactions",14,muted);pad(empty,0,24,0,24);return new Holder(empty,null,null,null,null);}
            LinearLayout container=column();LinearLayout item=row();pad(item,0,11,0,11);
            LinearLayout details=column();TextView description=text("",15,ink);description.setTypeface(null,1);details.addView(description);
            TextView meta=text("",11,muted);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-2);mp.topMargin=dp(4);details.addView(meta,mp);
            item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView amount=text("",13,green);amount.setTypeface(null,1);item.addView(amount);
            container.addView(item);View divider=new View(MainActivity.this);divider.setBackgroundColor(line);container.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            return new Holder(container,item,description,meta,amount);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            if(visible.isEmpty())return;
            int index=visible.get(position);JSONObject item=source.optJSONObject(index);if(item==null)return;
            String desc=item.optString("description","Transaction"),tag=item.optString("tag","");
            double amount=item.optDouble("amount",0);long time=item.optLong("time",0);
            h.description.setText(desc);
            h.meta.setText((tag.isEmpty()?"":Store.tagIcon(MainActivity.this,tag)+"  "+tag+"   ·   ")+Store.date(time));
            h.amount.setText((amount>=0?"+ ":"− ")+Store.money(Math.abs(amount)));h.amount.setTextColor(amount>=0?green:red);
            h.item.setForeground(getDrawable(android.R.drawable.list_selector_background));
            h.item.setOnClickListener(v->{if("Starting balance".equalsIgnoreCase(item.optString("description","")))showOpeningBalanceDialog();else showTransactionEditor(index,item);});
        }
        @Override public int getItemCount(){return visible.isEmpty()?1:visible.size();}
        final class Holder extends RecyclerView.ViewHolder{
            final LinearLayout item;final TextView description,meta,amount;
            Holder(View container,LinearLayout item,TextView description,TextView meta,TextView amount){super(container);this.item=item;this.description=description;this.meta=meta;this.amount=amount;}
        }
    }

    private int countMatchingTransactions(JSONArray arr,String query){
        int count=0;String q=query.toLowerCase(Locale.ROOT);
        for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;
            if(o.optString("description","").toLowerCase(Locale.ROOT).contains(q)||o.optString("tag","").toLowerCase(Locale.ROOT).contains(q))count++;
        }
        return count;
    }
    private void showTransactionSearch(){
        EditText query=field("Search descriptions or tags",false);
        query.setText(homeSearchQuery);
        LinearLayout box=column();pad(box,20,4,20,0);box.addView(query);
        new MaterialAlertDialogBuilder(this).setTitle("Search transactions").setView(box)
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Clear",(d,w)->{homeSearchQuery="";showMain();})
            .setPositiveButton("Search",(d,w)->{homeSearchQuery=query.getText().toString().trim();showMain();})
            .show();
    }

    private void showDaily(){
        pageBase("daily","Daily income");
        ScrollView content=new ScrollView(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,0,1);root.addView(content,cp);
        LinearLayout form=column();pad(form,0,4,0,8);content.addView(form);
        TextView hint=text("Automatically add this amount to your balance every day.",14,muted);form.addView(hint);
        EditText amount=field("Amount in PKR",true);amount.setText(Store.dailyEnabled(this)?String.valueOf(Store.dailyAmount(this)):"");LinearLayout.LayoutParams am=new LinearLayout.LayoutParams(-1,-2);am.topMargin=dp(24);form.addView(amount,am);
        selectedHour=Store.dailyHour(this);selectedMinute=Store.dailyMinute(this);Button time=button("◷   Time: "+String.format(Locale.US,"%02d:%02d",selectedHour,selectedMinute));LinearLayout.LayoutParams tm=new LinearLayout.LayoutParams(-1,-2);tm.topMargin=dp(14);form.addView(time,tm);
        time.setOnClickListener(v->new TimePickerDialog(this,(view,h,m)->{selectedHour=h;selectedMinute=m;time.setText("◷   Time: "+String.format(Locale.US,"%02d:%02d",h,m));},selectedHour,selectedMinute,true).show());
        Button save=button("Save daily income");LinearLayout.LayoutParams sm=new LinearLayout.LayoutParams(-1,-2);sm.topMargin=dp(18);form.addView(save,sm);
        TextView note=text("Daily credits are recorded as transactions. If Android delays an alarm, Batwa catches up when you next open the app.",12,muted);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(16);form.addView(note,np);
        save.setOnClickListener(v->{Double n=number(amount.getText().toString());if(n==null||n<=0){amount.setError("Enter a positive amount");return;}Store.saveDaily(this,n,selectedHour,selectedMinute);if(Build.VERSION.SDK_INT>=31&&!getSystemService(android.app.AlarmManager.class).canScheduleExactAlarms()){Intent intent=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));startActivity(intent);Toast.makeText(this,"Allow alarms, then return to Batwa",Toast.LENGTH_LONG).show();}else Alarm.schedule(this);Toast.makeText(this,"Daily income saved",Toast.LENGTH_SHORT).show();showDaily();});
        addNav();setContentView(root);
    }
    private void showSpending(){
        pageBase("spending","Spending by tag");
        TextView range=text("DATE RANGE",10,muted);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(12);root.addView(range,rp);
        LinearLayout dates=row();Button from=button("From: "+shortDate(rangeStart)),to=button("To: "+shortDate(rangeEnd));LinearLayout.LayoutParams fromParams=new LinearLayout.LayoutParams(0,-2,1);
        fromParams.rightMargin=dp(6);
        LinearLayout.LayoutParams toParams=new LinearLayout.LayoutParams(0,-2,1);
        toParams.leftMargin=dp(6);
        dates.addView(from,fromParams);dates.addView(to,toParams);root.addView(dates);
        from.setOnClickListener(v->pickDate(true,from,to));to.setOnClickListener(v->pickDate(false,from,to));
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams scp=new LinearLayout.LayoutParams(-1,0,1);scp.topMargin=dp(12);root.addView(scroll,scp);LinearLayout list=column();scroll.addView(list);
        java.util.LinkedHashMap<String,Double> totals=new java.util.LinkedHashMap<>();JSONArray tx=Store.transactions(this);
        for(int i=0;i<tx.length();i++)try{JSONObject o=tx.getJSONObject(i);double a=o.optDouble("amount",0);long t=o.optLong("time",0);if(a>=0||t<rangeStart||t>rangeEnd)continue;String tag=o.optString("tag","").trim();if(tag.isEmpty())tag="Untagged";totals.put(tag,totals.containsKey(tag)?totals.get(tag)+Math.abs(a):Math.abs(a));}catch(Exception ignored){}
        java.util.List<java.util.Map.Entry<String,Double>> sorted=new java.util.ArrayList<>(totals.entrySet());sorted.sort((a,b)->Double.compare(b.getValue(),a.getValue()));
        if(sorted.isEmpty()){TextView empty=text("No expenses in this date range",14,muted);pad(empty,0,24,0,24);list.addView(empty);}else for(java.util.Map.Entry<String,Double> e:sorted){String name=e.getKey();LinearLayout item=row();pad(item,0,14,0,14);TextView icon=text(name.equals("Untagged")?"▫️":Store.tagIcon(this,name),22,ink);item.addView(icon);TextView title=text(name,15,ink);title.setTypeface(null,1);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1);tlp.leftMargin=dp(12);item.addView(title,tlp);TextView value=text(Store.money(e.getValue()),14,red);value.setTypeface(null,1);item.addView(value);list.addView(item);View divider=new View(this);divider.setBackgroundColor(line);list.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
        addNav();setContentView(root);
    }
    private void pickDate(boolean isStart,Button from,Button to){Calendar c=Calendar.getInstance();c.setTimeInMillis(isStart?rangeStart:rangeEnd);new DatePickerDialog(this,(view,y,m,d)->{Calendar chosen=Calendar.getInstance();chosen.set(y,m,d);if(isStart)startDay(chosen);else endDay(chosen);long val=chosen.getTimeInMillis();if(isStart){if(val>rangeEnd){Toast.makeText(this,"Start date must be on or before end date",Toast.LENGTH_SHORT).show();return;}rangeStart=val;from.setText("From: "+shortDate(val));}else{if(val<rangeStart){Toast.makeText(this,"End date must be on or after start date",Toast.LENGTH_SHORT).show();return;}rangeEnd=val;to.setText("To: "+shortDate(val));}showSpending();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private void showTags(){
        pageBase("tags","Manage tags");syncIfSignedIn();Button add=button("+ Add tag");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(12);root.addView(add,ap);add.setOnClickListener(v->showTagEditor(null,null));
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams scp=new LinearLayout.LayoutParams(-1,0,1);scp.topMargin=dp(8);root.addView(scroll,scp);LinearLayout list=column();scroll.addView(list);JSONArray arr=Store.tags(this);
        if(arr.length()==0){TextView empty=text("No tags yet. Add a tag here or type one when adding an expense.",14,muted);pad(empty,0,20,0,20);list.addView(empty);}
        for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;String name=o.optString("name"),icon=o.optString("icon","🏷️");LinearLayout item=row();pad(item,0,10,0,10);TextView ico=text(icon,22,ink);item.addView(ico);TextView nm=text(name,15,ink);nm.setTypeface(null,1);LinearLayout.LayoutParams nmp=new LinearLayout.LayoutParams(0,-2,1);nmp.leftMargin=dp(12);item.addView(nm,nmp);Button edit=button("✎");edit.setTextSize(18);edit.setContentDescription("Edit "+name);
            LinearLayout.LayoutParams editParams=new LinearLayout.LayoutParams(dp(42),dp(40));editParams.leftMargin=dp(6);item.addView(edit,editParams);
            Button del=button("🗑");del.setTextSize(16);del.setContentDescription("Delete "+name);del.setTextColor(red);
            LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(dp(42),dp(40));deleteParams.leftMargin=dp(6);item.addView(del,deleteParams);edit.setOnClickListener(v->showTagEditor(name,icon));del.setOnClickListener(v->new MaterialAlertDialogBuilder(this).setTitle("Delete tag?").setMessage("Transactions using this tag will remain, but become untagged.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{Store.deleteTag(this,name);showTags();}).show());list.addView(item);View divider=new View(this);divider.setBackgroundColor(line);list.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
        addNav();setContentView(root);
    }
    private void showTransactionEditor(int index, JSONObject transaction){
        LinearLayout box=column();pad(box,20,4,20,0);
        EditText amount=field("Signed amount: + income / - expense",true);
        amount.setText(String.valueOf(transaction.optDouble("amount",0)));
        EditText description=field("Description",false);
        description.setText(transaction.optString("description",""));
        AutoCompleteTextView tag=new AutoCompleteTextView(this);
        tag.setSingleLine(true);tag.setHint("Tag (optional)");tag.setText(transaction.optString("tag",""));
        JSONArray tags=Store.tags(this);String[] names=new String[tags.length()];
        for(int i=0;i<tags.length();i++){JSONObject t=tags.optJSONObject(i);names[i]=t==null?"":t.optString("name");}
        tag.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,names));
        Calendar timestamp=Calendar.getInstance();timestamp.setTimeInMillis(transaction.optLong("time",System.currentTimeMillis()));
        Button dateTime=button("Date & time: "+Store.date(timestamp.getTimeInMillis()));
        amount.setHint("Amount (use minus for expense)");box.addView(amount);box.addView(description);box.addView(tag);box.addView(dateTime);
        dateTime.setOnClickListener(v->{
            Calendar c=(Calendar)timestamp.clone();
            new DatePickerDialog(this,(view,y,m,d)->{
                timestamp.set(Calendar.YEAR,y);timestamp.set(Calendar.MONTH,m);timestamp.set(Calendar.DAY_OF_MONTH,d);
                new TimePickerDialog(this,(tv,h,min)->{
                    timestamp.set(Calendar.HOUR_OF_DAY,h);timestamp.set(Calendar.MINUTE,min);
                    dateTime.setText("Date & time: "+Store.date(timestamp.getTimeInMillis()));
                },timestamp.get(Calendar.HOUR_OF_DAY),timestamp.get(Calendar.MINUTE),false).show();
            },timestamp.get(Calendar.YEAR),timestamp.get(Calendar.MONTH),timestamp.get(Calendar.DAY_OF_MONTH)).show();
        });
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("Edit transaction").setView(box)
                .setNegativeButton("Cancel",null).setNeutralButton("Delete",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                Double n=number(amount.getText().toString());
                String desc=description.getText().toString().trim(),tagName=tag.getText().toString().trim();
                if(n==null||n==0){amount.setError("Enter a non-zero amount");return;}
                if(desc.isEmpty()){description.setError("Enter a description");return;}
                if(!tagName.isEmpty()&&!Store.hasTag(this,tagName))Store.saveTag(this,tagName,"🏷️");
                Store.updateTransaction(this,index,desc,n,timestamp.getTimeInMillis(),tagName);
                dialog.dismiss();showMain();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->
                new MaterialAlertDialogBuilder(this).setTitle("Delete transaction?")
                    .setMessage("This will remove the transaction and adjust your balance.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                        Store.deleteTransaction(this,index);dialog.dismiss();showMain();
                    }).show());
        });
        dialog.show();
    }

    private void showTagEditor(String oldName,String oldIcon){
        boolean editing=oldName!=null;LinearLayout box=column();pad(box,20,4,20,0);EditText name=field("Tag name",false);if(editing)name.setText(oldName);box.addView(name);final String[] icon={editing?oldIcon:"🏷️"};Button choose=button("Choose icon  "+icon[0]);box.addView(choose);choose.setOnClickListener(v->showIconPicker(icon,choose));
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(editing?"Update tag":"New tag").setView(box).setNegativeButton("Cancel",null).setPositiveButton(editing?"Save":"Add",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Enter a tag name");return;}if(Store.hasTag(this,n)&&(!editing||!n.equalsIgnoreCase(oldName))){name.setError("This tag already exists");return;}if(editing)Store.renameTag(this,oldName,n,icon[0]);else Store.saveTag(this,n,icon[0]);dialog.dismiss();showTags();}));dialog.show();
    }

    /** Opens a virtualized searchable icon catalogue. RecyclerView creates only visible cells. */
    private void showIconPicker(String[] selected, Button trigger) {
        LinearLayout panel=column();
        pad(panel,16,4,16,8);
        EditText search=field("Search icons (e.g. cigarettes, internet, cup of tea)",false);
        search.setSingleLine(true);
        panel.addView(search,new LinearLayout.LayoutParams(-1,-2));
        TextView count=text("",12,muted);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(8);cp.bottomMargin=dp(6);panel.addView(count,cp);

        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new GridLayoutManager(this,5));
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(20);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(360));
        panel.addView(list,lp);

        activeIconSelection=selected;activeIconTrigger=trigger;
        IconGridAdapter adapter=new IconGridAdapter();
        list.setAdapter(adapter);
        Runnable refresh=()->{
            java.util.List<IconCatalog.Item> matches=IconCatalog.search(search.getText().toString());
            count.setText(matches.size()+" icons available");
            adapter.setItems(matches);
            list.scrollToPosition(0);
        };
        refresh.run();
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}
            @Override public void afterTextChanged(Editable e){}
        });
        AlertDialog picker=new MaterialAlertDialogBuilder(this).setTitle("Choose tag icon").setView(panel).setNegativeButton("Close",null).create();
        picker.show();
        if(picker.getWindow()!=null)picker.getWindow().setLayout(-1,-2);
        picker.setOnDismissListener(d->{activeIconSelection=null;activeIconTrigger=null;});
    }

    private final class IconGridAdapter extends RecyclerView.Adapter<IconGridAdapter.Holder> {
        private java.util.List<IconCatalog.Item> items=new java.util.ArrayList<>();
        void setItems(java.util.List<IconCatalog.Item> next){items=next;notifyDataSetChanged();}
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int viewType){
            MaterialButton b=new MaterialButton(MainActivity.this);
            b.setTextSize(22);b.setAllCaps(false);b.setInsetTop(0);b.setInsetBottom(0);
            b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);b.setCornerRadius(dp(12));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(52));
            lp.setMargins(dp(2),dp(2),dp(2),dp(2));b.setLayoutParams(lp);
            return new Holder(b);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            IconCatalog.Item item=items.get(position);
            holder.button.setText(item.emoji);
            holder.button.setContentDescription(item.keywords);
            holder.button.setOnClickListener(v->{
                // Selection is applied to the tag editor; closing the picker is optional.
                selectedIconCallback(item.emoji);
            });
        }
        @Override public int getItemCount(){return items.size();}
        final class Holder extends RecyclerView.ViewHolder {
            final MaterialButton button;
            Holder(MaterialButton b){super(b);button=b;}
        }
        private void selectedIconCallback(String emoji){
            // The active picker selection is updated by the callback installed below.
            if(activeIconSelection!=null){activeIconSelection[0]=emoji;}
            if(activeIconTrigger!=null)activeIconTrigger.setText("Choose icon  "+emoji);
        }
    }
    private String[] activeIconSelection;
    private Button activeIconTrigger;

    @Override protected void onResume(){super.onResume();if(Store.isSetup(this)){maybeCatchUp();syncIfSignedIn();if(root!=null){if(currentScreen.equals("daily"))showDaily();else if(currentScreen.equals("spending"))showSpending();else if(currentScreen.equals("tags"))showTags();else showMain();}}}
}
