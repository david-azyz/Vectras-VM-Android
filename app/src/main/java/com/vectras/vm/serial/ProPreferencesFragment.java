package com.vectras.vm.serial;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.vectras.vm.R;

import java.util.List;
import java.util.Locale;

/** Vectras PRO settings: COM port bridge and performance. */
public class ProPreferencesFragment extends PreferenceFragmentCompat {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pro_preferences, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        CollapsingToolbarLayout toolbar = requireActivity().findViewById(R.id.collapsingToolbarLayout);
        if (toolbar != null) toolbar.setSubtitle(getString(R.string.pro_title));
        handler.post(refresh);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    private void updateStatus() {
        Preference status = findPreference("proComStatus");
        if (status == null || getContext() == null) return;
        boolean pt = Locale.getDefault().getLanguage().equals("pt");
        List<UsbSerialDriver> drivers = UsbSerialHelper.findAllDrivers(requireContext());
        String adapter = drivers.isEmpty()
            ? (pt ? "Nenhum adaptador USB serial conectado" : "No USB serial adapter connected")
            : (pt ? "Adaptador: " : "Adapter: ") + UsbSerialHelper.getLabel(drivers.get(0));
        ComBridge bridge = ComBridge.get(requireContext());
        String state;
        switch (bridge.getState()) {
            case CONNECTED: state = pt ? "Ligado à VM" : "Linked to the VM"; break;
            case WAITING_VM: state = pt ? "Esperando a VM ligar" : "Waiting for the VM"; break;
            case WAITING_ADAPTER: state = pt ? "Esperando o adaptador USB" : "Waiting for the USB adapter"; break;
            case PERMISSION_DENIED: state = pt ? "Sem permissão para o USB" : "USB permission denied"; break;
            default: state = pt ? "Parado (inicia junto com a VM)" : "Stopped (starts with the VM)";
        }
        String traffic = String.format(Locale.US, pt ? "Enviados: %d bytes  |  Recebidos: %d bytes" : "Sent: %d bytes  |  Received: %d bytes",
            bridge.getBytesToDevice(), bridge.getBytesFromDevice());
        status.setSummary(adapter + "\n" + state + "\n" + traffic);
    }
}
