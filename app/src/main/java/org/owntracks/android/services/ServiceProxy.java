package org.owntracks.android.services;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.LinkedList;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;

import org.owntracks.android.App;
import org.owntracks.android.support.StatisticsProvider;
import org.owntracks.android.support.interfaces.ProxyableService;
import org.owntracks.android.support.receiver.ReceiverProxy;
import org.owntracks.android.support.unfree.GoogleApiAvailability;

import timber.log.Timber;

public class ServiceProxy extends Service {
	private static final String TAG = "ServiceProxy";

    public static final String WAKELOCK_TAG_BROKER_PING = "org.owntracks.android.wakelock.broker.ping";
	public static final int SERVICE_LOCATOR = 0;
	public static final int SERVICE_NOTIFICATION = 1;
	public static final int SERVICE_BEACON = 2;
	public static final int SERVICE_MESSAGE = 3;


	public static final String KEY_SERVICE_ID = "srvID";
	private static ServiceProxy instance;
	private static final ProxyableService[] services = new ProxyableService[4];
	private static final LinkedList<Runnable> runQueue = new LinkedList<>();
	private static ServiceProxyConnection connection;
	private static boolean bound = false;
    private static boolean attemptingToBind = false;
	private static boolean bgInitialized = false;




	protected boolean started;
	protected ServiceBinder binder;

	public static void setBgInitialized() {
		synchronized (ServiceProxy.class) {
			bgInitialized = true;
		}
	}

	private static boolean getBgInitialized() {
		synchronized (ServiceProxy.class) {
			return bgInitialized;
		}
	}



	@Override
	public void onCreate() {
		super.onCreate();
		this.binder = new ServiceBinder(this);
		HandlerThread mServiceHandlerThread = new HandlerThread("ServiceThread");
		mServiceHandlerThread.start();

	}

	private void onStartOnce(@Nullable final Intent intent) {
		instance = this;
		Timber.v("");

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				Timber.d("loading services");
				loadService(SERVICE_NOTIFICATION);
				loadService(SERVICE_MESSAGE);
				loadService(SERVICE_LOCATOR);
				loadService(SERVICE_BEACON);
				setBgInitialized();
				deliverIntentToService(intent);
				App.postOnMainHandler(new Runnable() {
					@Override
					public void run() {

						runQueue();
					}
				});
			}
		};

		App.postOnBackgroundHandler(runnable);
		StatisticsProvider.setTime(StatisticsProvider.SERVICE_PROXY_START);

	}

	public static ServiceProxy getInstance() {
		return instance;
	}

	@Override
	public void onDestroy() {
		Timber.v("");
		for ( int i = 1; i <= 4; i ++ ) {
			App.getEventBus().unregister(services[i]);
			services[i].onDestroy();
			services[i]=null;
		}
		bgInitialized = false;
		if (this.binder != null) {
			this.binder.close();
			this.binder = null;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		start(intent);
		return Service.START_STICKY;
	}

	void start(@Nullable final Intent intent) {

		if (getBgInitialized() && intent != null) {
			Timber.v("case:1/direct");
			// start was already called and services are setup. Deliver intent to service if there is one
			deliverIntentToService(intent);
		} else if(started && intent != null) {
			Timber.v("case:2/started/notrdy");
			// start was already called but services are not yet ready to handle intent.
			// Race condition were new intent is received before services are ready.
			// Intent is queued into queue of runnables that run when all services are ready. See onStartOnce();
			runQueue.addLast(new Runnable() {
				@Override
				public void run() {
					Timber.v("delivering intent to service ");
					deliverIntentToService(intent);
				}
			});
		} else {
			Timber.v("case:3/notstarted");
			// start services and deliver intent when ready.
			started = true;
			onStartOnce(intent);
		}
	}


	private void deliverIntentToService(@Nullable Intent intent) {
		ProxyableService s = getServiceForIntent(intent);
		Log.v(TAG, "deliverIntentToService getServiceForIntent:"+s);
		if (s != null && intent != null)
			s.onStartCommand(intent);

	}

	public static ProxyableService loadService(int id) {
		ProxyableService p = services[id];

		if (p != null)
			return p;

		Timber.v("lazyloading:%s", id);
        switch (id) {
			case SERVICE_LOCATOR:
                p = new ServiceLocator();
                break;
            case SERVICE_BEACON:
                p = new ServiceBeacon();
                break;
			case SERVICE_NOTIFICATION:
				p = new ServiceNotification();
				break;
			case SERVICE_MESSAGE:
				p = new ServiceMessage();
				break;
		}

		if(p == null)
			return null;

		services[id] = p;
		p.onCreate(instance);
		App.getEventBus().register(p);

		Timber.v("subscribed to messagelocation:%s", App.getEventBus().hasSubscriberForEvent(org.owntracks.android.messages.MessageLocation.class));

		return p;
	}

	public static ServiceLocator getServiceLocator() {
		return (ServiceLocator) loadService(SERVICE_LOCATOR);
	}

    public static ServiceBeacon getServiceBeacon() {
        return (ServiceBeacon) loadService(SERVICE_BEACON);
    }
	public static ServiceNotification getServiceNotification() {
		return (ServiceNotification) loadService(SERVICE_NOTIFICATION);
	}
	public static ServiceMessage getServiceMessage() {
		return (ServiceMessage) loadService(SERVICE_MESSAGE);
	}

	public static ProxyableService getServiceForIntent(Intent i) {
		if ((i != null) && (i.getIntExtra(KEY_SERVICE_ID, -1) != -1))
			return loadService(i.getIntExtra(KEY_SERVICE_ID, -1));
		else
			return null;

	}

	public static PendingIntent getBroadcastIntentForService(Context c,  int targetServiceId, String action, Bundle extras) {
		return getBroadcastIntentForService(c, targetServiceId, action, extras, -1);
	}

	public static PendingIntent getBroadcastIntentForService(Context c,  int targetServiceId, String action, Bundle extras, int flags) {
		Intent i = new Intent().setClass(c, ReceiverProxy.class);
		i.setAction(action);

		if (extras != null)
			i.putExtras(extras);
		i.putExtra(KEY_SERVICE_ID, targetServiceId);

		return PendingIntent.getBroadcast(c, 0, i, flags != -1 ? flags : PendingIntent.FLAG_CANCEL_CURRENT);
	}




	public static PendingIntent getPendingIntentForService(Context c,
			int targetServiceId, String action, Bundle extras) {
		return getPendingIntentForService(c, targetServiceId, action, extras,
				PendingIntent.FLAG_CANCEL_CURRENT);
	}

	public static PendingIntent getPendingIntentForService(Context c, int targetServiceId, String action, Bundle extras, int flags) {
		Intent i = new Intent().setClass(c, ServiceProxy.class);
		i.setAction(action);

		if (extras != null)
			i.putExtras(extras);
		i.putExtra(KEY_SERVICE_ID, targetServiceId);

		return PendingIntent.getService(c, 0, i, flags);

	}

	public static void stopService(ProxyableService service) {
		//TODO
	}


	public final static class ServiceProxyConnection implements Closeable {
		private final Context context;
		private final ServiceConnection serviceConnection;

		private ServiceProxyConnection(Context context,
				ServiceConnection serviceConnection) {
			this.context = context;
			this.serviceConnection = serviceConnection;
		}

		@Override
		public void close() {
			Timber.e("service connection closed");
            attemptingToBind = false;

            if (bound) {
				this.context.unbindService(this.serviceConnection);
				bound = false;
			}
		}

		public ServiceConnection getServiceConnection() {
			return this.serviceConnection;
		}

	}

    // No bind, only acting on static methods and tearing down service connection anyway
	public static void closeServiceConnection() {
		if ((getServiceConnection() != null) && bound)
            getServiceConnection().close();
	}

	public static ServiceProxyConnection getServiceConnection() {
		return connection;
	}


	private static void runQueue() {
		Timber.v("queue length:%s", runQueue.size());
		for (Runnable r : runQueue)
			r.run();
		runQueue.clear();

	}
	public static void bind(@NonNull  Context context) {
		runOrBind(context, null);
	}
	public static void runOrBind(@NonNull  Context context, @Nullable Runnable runnable) {
		if (((instance != null ) && (getServiceConnection() != null)) || context instanceof ServiceProxy) {

			if(runnable != null)
            	runnable.run();
			return;
		}

		if (getServiceConnection() == null) {
			ServiceConnection c = new ServiceConnection() {
				@Override
				public void onServiceDisconnected(ComponentName name) {
					bound = false;
				}

				@Override
				public void onServiceConnected(ComponentName name, IBinder binder) {
					instance = ServiceBinder.class.cast(binder).getService(); // this leaks the service but we actually want that
                    bound = true;
                    attemptingToBind = false;
					if(getBgInitialized())
						runQueue();
					else
						Timber.d("service connected but bgInitialized not finished");
				}
			};
			connection = new ServiceProxyConnection(context, c);
		}
		if(runnable != null)
			runQueue.addLast(runnable);

        try {
            if (!attemptingToBind) { // Prevent accidental bind during close
                attemptingToBind = true;
                context.bindService(new Intent(context, ServiceProxy.class), connection.getServiceConnection(), Context.BIND_AUTO_CREATE);
            }
        } catch (Exception e) {
            Log.e("ServiceProxy", "bind exception ");
            e.printStackTrace();
            attemptingToBind = false;
        }
	}

	public static void onEnterForeground() {
		if(bgInitialized) {
			getServiceLocator().onEnterForeground();
			getServiceBeacon().onEnterForeground();
		}
	}

	public static void onEnterBackground() {
		if(bgInitialized) {
			getServiceLocator().onEnterBackground();
			getServiceBeacon().onEnterBackground();
		}
	}

	public static boolean isInForeground() {
		return App.isInForeground();
	}


	@Override
	public IBinder onBind(Intent intent) {
		Timber.v("");
		start(intent);
		return this.binder;
	}

	public class ServiceBinder extends Binder {
		private WeakReference<ServiceProxy> mService;

		ServiceBinder(ServiceProxy serviceBindable) {
			this.mService = new WeakReference<>(serviceBindable);
		}

		public ServiceProxy getService() {
			return this.mService.get();
		}

		void close() {
			this.mService = null;
		}
	}

	protected static boolean checkPlayServices() {
		//return GoogleApiAvailability.checkPlayServices(App.getContext());
		return  org.owntracks.android.support.unfree.GoogleApiAvailability.checkPlayServicesWithOverride();
	}
}
