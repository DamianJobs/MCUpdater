package org.mcupdater;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.mcupdater.mojang.AssetManager;
import org.mcupdater.settings.SettingsManager;
import org.mcupdater.translate.TranslateProxy;
import org.mcupdater.util.MCUpdater;
import org.mcupdater.util.Module;
import org.mcupdater.util.ServerList;
import org.mcupdater.util.ServerPackParser;
import org.mcupdater.translate.Languages;

public class MainShell extends MCUApp {

	private static MainShell INSTANCE;
	protected Shell shell;
	private ServerList selected;
	private MCUBrowser browser;
	private List<Module> modList;
	private MCUConsole console;
	private MCUProgress progress;
	private MCUModules modules;
	private SettingsManager sManager = SettingsManager.getInstance();
	public TranslateProxy translate;
	private Label lblStatus;

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			INSTANCE = new MainShell();
			INSTANCE.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 */
	public void open() {
		final Display display = Display.getDefault();
		try {
			translate = Languages.valueOf(Languages.getLocale()).getProxy();
		} catch (Exception e) {
			System.out.println("No translation for " + Languages.getLocale() + "!");
			translate = Languages.en_US.getProxy();
		}		
		createContents();
		shell.open();
		shell.layout();
		TrackerListener tracker = new TrackerListener(){

			@Override
			public void onQueueFinished(final DownloadQueue queue) {
				System.out.println(queue.getName() + ": Finished!");
				display.syncExec(new Runnable(){
					@Override
					public void run() {
						if (progress == null || progress.isDisposed()) { return; }
						progress.updateProgress(queue.getName(),1.0F,queue.getTotalFileCount(),queue.getSuccessFileCount());
					}
				});

			}

			@Override
			public void onQueueProgress(final DownloadQueue queue) {
				//System.out.println(queue.getName() + ": " + (100.0F * queue.getProgress()) + "%");
				display.syncExec(new Runnable(){
					@Override
					public void run() {
						if (progress == null || progress.isDisposed()) { return; }
						progress.updateProgress(queue.getName(),queue.getProgress(),queue.getTotalFileCount(),queue.getSuccessFileCount());
					}
				});
			}

			@Override
			public void printMessage(String msg) {
				System.out.println(msg);				
			}
			
		};
		final DownloadQueue assetsQueue = AssetManager.downloadAssets(MCUpdater.getInstance().getArchiveFolder().resolve("assets").toFile(), tracker);
		progress.addProgressBar(assetsQueue.getName());
		ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 8, 30000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
			if (!assetsQueue.isActive()) {
				assetsQueue.processQueue(executor);			
			}
		}
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell();
		shell.setSize(1175, 592);
		shell.setText("MCUpdater 3.1.0");
		shell.setLayout(new GridLayout(1,false));
		
		shell.addListener(SWT.RESIZE, new Listener() {

			@Override
			public void handleEvent(Event arg0) {
				shell.layout();
			}
		});
		
		final Composite mainArea = new Composite(shell, SWT.NONE);
		mainArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		mainArea.setLayout(new FormLayout());
		{
			final int limit = 20;
			final Sash sashLeft = new Sash(mainArea, SWT.VERTICAL);

			Group grpInstances = new Group(mainArea, SWT.NONE);
			grpInstances.setText(translate.instances);
			grpInstances.setLayout(new FillLayout());
			FormData grpInstancesData = new FormData();
			{
				grpInstancesData.left = new FormAttachment(0,0);
				grpInstancesData.right = new FormAttachment(sashLeft, 0);
				grpInstancesData.top = new FormAttachment(0,0);
				grpInstancesData.bottom = new FormAttachment(100,0);
			}
			grpInstances.setLayoutData(grpInstancesData);			
			{
				/*				
				Composite foo = new Composite(grpInstances, SWT.NONE);
				foo.setLayout(new RowLayout(SWT.VERTICAL));
				List<ServerList> list = MCUpdater.getInstance().loadServerList("http://files.mcupdater.com/example/SamplePack.xml");
				Collections.sort(list);
				for (ServerList entry : list) {
					Label bar = new Label(foo, SWT.NONE);
					bar.setText("Entry: " + entry.getName());
				}
				*/
				InstanceList iList = new InstanceList(grpInstances);
				iList.setInstances(MCUpdater.getInstance().loadServerList("http://files.mcupdater.com/example/SamplePack.xml"));
				grpInstances.pack();
			}
			final FormData sashLeftData = new FormData();
			{
				sashLeftData.left = new FormAttachment(20, 0);
				sashLeftData.top = new FormAttachment(0, 0);
				sashLeftData.bottom = new FormAttachment(100, 0);
			}
			sashLeft.setLayoutData(sashLeftData);
			sashLeft.addListener(SWT.Selection, new Listener(){
				@Override
				public void handleEvent(Event e) {
					Rectangle sashRect = sashLeft.getBounds();
					Rectangle viewRect = mainArea.getClientArea();
					int right = viewRect.width - sashRect.width - limit;
					e.x = Math.max(Math.min(e.x, right), limit);
					if (e.x != sashRect.x) {
						sashLeftData.left = new FormAttachment(0, e.x);
						mainArea.layout();
					}
				}
			});
			
			final TabFolder tabFolder = new TabFolder(mainArea, SWT.NONE);
			final FormData grpTabData = new FormData();
			{
				grpTabData.left = new FormAttachment(sashLeft,0);
				grpTabData.right = new FormAttachment(100,0);
				grpTabData.top = new FormAttachment(0,0);
				grpTabData.bottom = new FormAttachment(100,0);
			}
			tabFolder.setLayoutData(grpTabData);
			{
				TabItem tbtmNews = new TabItem(tabFolder, SWT.V_SCROLL);
				{
					tbtmNews.setText(translate.news);
					browser = new MCUBrowser(tabFolder, SWT.NONE);
					tbtmNews.setControl(browser);
				}
				TabItem tbtmConsole = new TabItem(tabFolder, SWT.NONE);
				{
					tbtmConsole.setText(translate.console);
					console = new MCUConsole(tabFolder);
					tbtmConsole.setControl(console);
				}
				TabItem tbtmSettings = new TabItem(tabFolder, SWT.NONE);
				{
					tbtmSettings.setText(translate.settings);
					MCUSettings cmpSettings = new MCUSettings(tabFolder);
					tbtmSettings.setControl(cmpSettings);
				}
				TabItem tbtmModules = new TabItem(tabFolder, SWT.NONE);
				{
					tbtmModules.setText(translate.modules);
					modules = new MCUModules(tabFolder);
					tbtmModules.setControl(modules);
				}
				TabItem tbtmProgress = new TabItem(tabFolder, SWT.NONE);
				{
					tbtmProgress.setText(translate.progress);
					progress = new MCUProgress(tabFolder);
					tbtmProgress.setControl(progress);
				}
			}
			
			/*
			Group grpModules = new Group(mainArea, SWT.NONE);
			grpModules.setText("Modules");
			final FormData grpModulesData = new FormData();
			{
				grpModulesData.left = new FormAttachment(sashRight,0);
				grpModulesData.right = new FormAttachment(100, 0);
				grpModulesData.top = new FormAttachment(0,0);
				grpModulesData.bottom = new FormAttachment(100,0);
			}
			grpModules.setLayoutData(grpModulesData);
			grpModules.setLayout(new FillLayout(SWT.VERTICAL));
			ScrolledComposite modScroller = new ScrolledComposite(grpModules, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
			modContainer = new Composite(modScroller, SWT.FILL);
			modContainer.setLayout(new RowLayout(SWT.VERTICAL));
			modScroller.setContent(modContainer);
			*/
			
//			final FormData sashRightData = new FormData();
//			{
//				sashRightData.right = new FormAttachment(80,0);
//				sashRightData.top = new FormAttachment(0, 0);
//				sashRightData.bottom = new FormAttachment(100, 0);
//			}
//			sashRight.setLayoutData(sashRightData);
//			sashRight.addListener(SWT.Selection, new Listener(){
//				@Override
//				public void handleEvent(Event e) {
//					Rectangle sashRect = sashRight.getBounds();
//					Rectangle viewRect = mainArea.getClientArea();
//					int right = viewRect.width - sashRect.width - limit;
//					e.x = Math.max(Math.min(e.x, right), limit);
//					if (e.x != sashRect.x) {
//						sashRightData.right = new FormAttachment(0, e.x);
//						mainArea.layout();
//					}
//				}
//			});
		}
		
		Composite cmpStatus = new Composite(shell, SWT.NONE);
		cmpStatus.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		{
			cmpStatus.setLayout(new GridLayout(4, false));
						
			lblStatus = new Label(cmpStatus, SWT.NONE);
			lblStatus.setLayoutData(new GridData(SWT.FILL,SWT.CENTER,true,false,1,1));
			lblStatus.setText("Ready");
			
			Composite login = new MCULogin(cmpStatus);
			login.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER,false,false,1,1));

			Button btnUpdate = new Button(cmpStatus, SWT.PUSH);
			btnUpdate.setLayoutData(new GridData(SWT.LEFT,SWT.CENTER,false,false,1,1));
			btnUpdate.setText(translate.update);
			
			Button btnLaunch = new Button(cmpStatus, SWT.PUSH);
			btnLaunch.setLayoutData(new GridData(SWT.LEFT,SWT.CENTER,false,false,1,1));
			btnLaunch.setText(translate.launchMinecraft);
			btnLaunch.addSelectionListener(new SelectionListener()
			{

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
					widgetSelected(arg0);
				}

				@Override
				public void widgetSelected(SelectionEvent arg0) {
					MCULogic.doLaunch();
				}
				
			});
		}
	}
	
	public void changeSelectedInstance(ServerList entry) {
		this.selected = entry;
		browser.setUrl(selected.getNewsUrl());
		modList = ServerPackParser.loadFromURL(selected.getPackUrl(), selected.getServerId());
		refreshModList();
	}

	private void refreshModList() {
		modules.reload(modList);
	}

	public static MainShell getInstance() {
		return INSTANCE;
	}

	public SettingsManager getSettingsManager() {
		return sManager;
	}

	@Override
	public void setStatus(String string) {
		// TODO Auto-generated method stub
		lblStatus.setText(string);
	}

	@Override
	public void setProgressBar(int i) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void log(String msg) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean requestLogin() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void addServer(ServerList entry) {
		// TODO Auto-generated method stub
		
	}
}
