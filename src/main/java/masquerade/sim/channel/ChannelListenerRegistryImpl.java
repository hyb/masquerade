package masquerade.sim.channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import masquerade.sim.model.Channel;
import masquerade.sim.model.ChannelListener;
import masquerade.sim.model.SimulationRunner;

public class ChannelListenerRegistryImpl implements ChannelListenerRegistry {

	private static final Logger log = Logger.getLogger(ChannelListenerRegistryImpl.class.getName());
	
	private Map<String, ChannelListener<?>> channels = new LinkedHashMap<String, ChannelListener<?>>();
	private SimulationRunner simulationRunner;
	
	public ChannelListenerRegistryImpl(SimulationRunner simulationRunner) {
		this.simulationRunner = simulationRunner;
	}

	@Override
	public <T extends ChannelListener<?>> T getChannelListener(String name, Class<T> channelListenerType) {
		synchronized (channels) {
			@SuppressWarnings("unchecked")
			T listener = (T) channels.get(name);
			return listener;
		}
	}
	
	@Override
	public <T extends ChannelListener<?>> Collection<T> getAllListeners(Class<T> channelListenerTye) {
		Collection<T> ret = new ArrayList<T>();
		for (ChannelListener<?> listener : channels.values()) {
			if (channelListenerTye.isAssignableFrom(listener.getClass())) {
				ret.add(channelListenerTye.cast(listener));
			}
		}
		return ret;
	}

	@Override
	public void notifyChannelChanged(Channel changedChannel) {
		synchronized (channels) {
			@SuppressWarnings("unchecked")
			ChannelListener<Channel> listener = getChannelListener(changedChannel.getName(), ChannelListener.class);
			
			if (listener == null) {
				startChannel(changedChannel);
			} else {
				restartChannel(listener, changedChannel);
			}
		}
	}

	@Override
	public void notifyChannelDeleted(String name) {
		synchronized (channels) {
			ChannelListener<?> listener = getChannelListener(name, ChannelListener.class);
			if (listener != null) {
				log.log(Level.INFO, "Stopping channel " + name);

				channels.remove(name);
				listener.stop();
			}
		}
	}	
	
	private void startChannel(Channel changedChannel) {
		synchronized (channels) {
			String channelName = changedChannel.getName();
			if (changedChannel.isActive()) {
				log.log(Level.INFO, "Starting channel " + channelName);
				ChannelListener<Channel> listener = createListener(changedChannel.getListenerType());
				listener.start(changedChannel, simulationRunner);
				channels.put(channelName, listener);
			} else {
				log.log(Level.INFO, "Skipping channel start for inactive channel " + channelName);
			}
		}
	}	
	
	private ChannelListener<Channel> createListener(Class<? extends ChannelListener<?>> listenerType) {
		try {
			@SuppressWarnings("unchecked")
			ChannelListener<Channel> listener = (ChannelListener<Channel>) listenerType.newInstance();
			return listener;
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Cannot instantiate channel listener: " + listenerType.getName(), e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Cannot instantiate channel listener: " + listenerType.getName(), e);
		}
	}

	private void restartChannel(ChannelListener<Channel> listener, Channel changedChannel) {
		log.log(Level.INFO, "Restarting channel " + changedChannel);
		listener.stop();
		listener.start(changedChannel, simulationRunner);
	}
}