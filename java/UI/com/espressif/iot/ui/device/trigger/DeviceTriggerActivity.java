package com.espressif.iot.ui.device.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import com.espressif.iot.R;
import com.espressif.iot.device.IEspDevice;
import com.espressif.iot.type.device.trigger.EspDeviceTrigger;
import com.espressif.iot.type.device.trigger.EspDeviceTrigger.TriggerRule;
import com.espressif.iot.ui.main.EspActivityAbs;
import com.espressif.iot.user.IEspUser;
import com.espressif.iot.user.builder.BEspUser;
import com.espressif.iot.util.EspStrings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceTriggerActivity extends EspActivityAbs implements OnItemClickListener {
    private final Logger log = Logger.getLogger(getClass());

    private static final int TRIGGER_NUM = 4;

    public static final String[] COMPARE_TYPE_ARRAY = new String[] {"=", "!=", ">", ">=", "<", "<="};

    private IEspUser mUser;
    private IEspDevice mDevice;

    private ListView mTriggerListView;
    private List<EspDeviceTrigger> mTriggerList;
    private BaseAdapter mTriggerAdapter;

    private FragmentManager mFragmentManager;
    private static final String TAG_FRAGMENT = "Settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.device_trigger_activity);

        Intent intent = getIntent();
        String deviceKey = intent.getStringExtra(EspStrings.Key.DEVICE_KEY_KEY);
        mUser = BEspUser.getBuilder().getInstance();
        mDevice = mUser.getUserDevice(deviceKey);

        mTriggerListView = (ListView)findViewById(R.id.device_trigger_list);
        mTriggerList = new ArrayList<EspDeviceTrigger>();
        mTriggerAdapter = new TriggerAdapter(this);
        mTriggerListView.setAdapter(mTriggerAdapter);
        mTriggerListView.setOnItemClickListener(this);

        mFragmentManager = getFragmentManager();

        new GetTriggerTask(this).execute();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        EspDeviceTrigger trigger = mTriggerList.get(position);
        EspTriggerSettingsFragment fragment = new EspTriggerSettingsFragment();
        fragment.setTrigger(trigger);
        fragment.setDevice(mDevice);

        mFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, TAG_FRAGMENT)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .addToBackStack(null)
            .commit();
    }

    /**
     * Check the triggers list from server, find dimension[x, y, z, k], if not found, create it
     * 
     * @param triggers
     */
    private void onGetTriggers(List<EspDeviceTrigger> triggers) {
        mTriggerList.addAll(triggers);
        mTriggerAdapter.notifyDataSetChanged();

        List<Integer> addTriggerDimensions = new ArrayList<Integer>();
        for (int i = 0; i < TRIGGER_NUM; i++) {
            boolean exist = false;

            for (EspDeviceTrigger trigger : triggers) {
                if (i == trigger.getDimension()) {
                    exist = true;
                    break;
                }
            }

            if (!exist) {
                log.debug("onGetTriggers need create dimension " + i);
                addTriggerDimensions.add(i);
            }
        }

        if (!addTriggerDimensions.isEmpty()) {
            new CreateTriggerTask(this, addTriggerDimensions).execute();
        }
    }

    private class TriggerAdapter extends BaseAdapter {
        private Activity mActivity;

        public TriggerAdapter(Activity activity) {
            mActivity = activity;
        }

        @Override
        public int getCount() {
            return mTriggerList.size();
        }

        @Override
        public Object getItem(int position) {
            return mTriggerList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mTriggerList.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(mActivity, android.R.layout.simple_list_item_2, null);
            }

            EspDeviceTrigger trigger = mTriggerList.get(position);

            TextView tv1 = (TextView)convertView.findViewById(android.R.id.text1);
            tv1.setText(trigger.getName());
            List<TriggerRule> rules = trigger.getTriggerRules();
            if (!rules.isEmpty()) {
                TextView tv2 = (TextView)convertView.findViewById(android.R.id.text2);
                TriggerRule rule = rules.get(0);

                StringBuilder ruleStr = new StringBuilder();
                ruleStr.append(COMPARE_TYPE_ARRAY[trigger.getCompareType()]).append(" ");
                ruleStr.append(trigger.getCompareValue()).append(", ");
                ruleStr.append(rule.toString());
                if (rules.size() > 1) {
                    ruleStr.append("...");
                }

                tv2.setText(ruleStr);
            }

            return convertView;
        }
    }

    /**
     * Sort by dimension
     */
    private Comparator<EspDeviceTrigger> mTriggerComparator = new Comparator<EspDeviceTrigger>() {

        @Override
        public int compare(EspDeviceTrigger lhs, EspDeviceTrigger rhs) {
            Integer l = lhs.getDimension();
            Integer r = rhs.getDimension();
            return l.compareTo(r);
        }
    };

    private class GetTriggerTask extends ProgressDialogTask<Void, Void, List<EspDeviceTrigger>> {

        public GetTriggerTask(Activity activity) {
            super(activity);
        }

        @Override
        protected List<EspDeviceTrigger> doInBackground(Void... params) {
            log.debug("execute GetTriggerTask");
            List<EspDeviceTrigger> result = mUser.doActionDeviceTriggerGet(mDevice);
            Collections.sort(result, mTriggerComparator);
            return result;
        }

        @Override
        protected void onPostExecute(List<EspDeviceTrigger> result) {
            super.onPostExecute(result);

            if (result == null) {
                log.info("GetTriggerTask result null");
                Toast.makeText(mContext, R.string.esp_device_trigger_get_failed, Toast.LENGTH_LONG).show();
            }
            else {
                log.info("GetTriggerTask result size = " + result.size());
                onGetTriggers(result);
            }
        }
    }

    private class CreateTriggerTask extends ProgressDialogTask<Void, Void, List<EspDeviceTrigger>> {
        private List<Integer> mDimensionList;

        public CreateTriggerTask(Activity activity, List<Integer> dimensions) {
            super(activity);

            mDimensionList = dimensions;
        }

        @Override
        protected List<EspDeviceTrigger> doInBackground(Void... params) {
            log.debug("execute CreateTriggerTask");
            List<EspDeviceTrigger> result = new ArrayList<EspDeviceTrigger>();
            for (int dimension : mDimensionList) {
                EspDeviceTrigger trigger = new EspDeviceTrigger();
                trigger.setDimension(dimension);

                String name = "";
                switch (dimension) {
                    case 0:
                        name = "event x";
                        break;
                    case 1:
                        name = "event y";
                        break;
                    case 2:
                        name = "event z";
                        break;
                    case 3:
                        name = "event k";
                        break;
                }
                trigger.setName(name);

                trigger.setStreamType(EspDeviceTrigger.STREAM_LIGHT);
                trigger.setInterval(0);
                trigger.setIntervalFunc(EspDeviceTrigger.INTERVAL_FUNC_AVG);
                trigger.setCompareType(EspDeviceTrigger.COMPARE_TYPE_EQ);
                trigger.setCompareValue(0);

                long id = mUser.doActionDeviceTriggerCreate(mDevice, trigger);
                log.info("create dimension " + dimension + " result id = " + id);
                if (id > 0) {
                    result.add(trigger);
                }
            }

            if (result.size() > 0) {
                result.addAll(mTriggerList);
                Collections.sort(result, mTriggerComparator);
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<EspDeviceTrigger> result) {
            super.onPostExecute(result);

            if (result.size() > 0) {
                mTriggerList.clear();
                mTriggerList.addAll(result);
                mTriggerAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onBackPressed() {
        /*
         * if find fragment and the fragment hasn't saved it's changes, hit user
         */
        Fragment fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT);
        if (fragment != null) {
            if (!((EspTriggerSettingsFragment)fragment).hasSavedChanges()) {
                new AlertDialog.Builder(this).setMessage(R.string.esp_device_trigger_settings_back_msg)
                    .setPositiveButton(R.string.esp_device_trigger_settings_back_exit,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                superBack();
                            }
                        })
                    .show();

                return;
            }
        }

        super.onBackPressed();
    }

    private void superBack() {
        super.onBackPressed();
    }
}
