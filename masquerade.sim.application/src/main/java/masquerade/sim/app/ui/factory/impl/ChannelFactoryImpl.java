package masquerade.sim.app.ui.factory.impl;

import masquerade.sim.app.ui.factory.ChannelFactory;
import masquerade.sim.model.Channel;
import masquerade.sim.model.ModelInstanceTypeProvider;
import masquerade.sim.model.listener.CreateApprover;
import masquerade.sim.model.listener.CreateListener;
import masquerade.sim.model.repository.ModelRepository;
import masquerade.sim.model.ui.CreateNamedObjectDialog;
import masquerade.sim.plugin.PluginRegistry;

import com.vaadin.ui.Window;

/**
 * Shows a model dialog for the user to select a channel implementation and
 * enter an ID for the new channel.
 */
public class ChannelFactoryImpl implements ChannelFactory {

	private final PluginRegistry pluginRegistry;
	private final ModelRepository modelRepository;
	private final Window window;

	public ChannelFactoryImpl(PluginRegistry pluginRegistry, ModelRepository modelRepository, Window window) {
		this.pluginRegistry = pluginRegistry;
		this.modelRepository = modelRepository;
		this.window = window;
	}

	@Override
	public void createChannel(final ChannelFactoryCallback callback) {
		ModelInstanceTypeProvider instanceTypeProvider = new ModelInstanceTypeProvider(Channel.class, pluginRegistry);
		CreateApprover createApprover = new CreateApprover() {
			@Override
			public boolean isNameUsed(String usedName) {
				return modelRepository.containsChannel(usedName);
			}
		};		
		CreateListener createListener = new CreateListener() {
			@Override
			public void notifyCreate(Object value) {
				Channel channel = (Channel) value;
				callback.onCreate(channel);
			}
		};
		
		CreateNamedObjectDialog.showModal(window, "Create Channel", "newChannelId", createListener, createApprover, instanceTypeProvider);
	}

}
