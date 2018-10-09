package wusc.edu.pay.app.notify;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import wusc.edu.pay.app.notify.core.NotifyQueue;
import wusc.edu.pay.app.notify.core.NotifyTask;
import wusc.edu.pay.common.page.PageBean;
import wusc.edu.pay.common.page.PageParam;
import wusc.edu.pay.facade.notify.entity.NotifyRecord;
import wusc.edu.pay.facade.notify.service.NotifyFacade;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.DelayQueue;

/**
 * Spring Boot 应用启动类
 *
 * Created by bysocket on 16/4/26.
 */
// Spring Boot 应用的标识
@SpringBootApplication
// mapper 接口类扫描包配置
@MapperScan("wusc.edu.pay")
@EnableCaching // 启动缓存
public class Application {


	private static final Log LOG = LogFactory.getLog(Application.class);

	public static DelayQueue<NotifyTask> tasks = new DelayQueue<NotifyTask>();

	private static ClassPathXmlApplicationContext context;

	private static ThreadPoolTaskExecutor threadPool;

	public static NotifyFacade notifyFacade;

	private static NotifyQueue notifyQueue;


	public static void main(String[] args) {
		// 程序启动入口
		// 启动嵌入式的 Tomcat 并初始化 Spring 环境及其各 Spring 组件
		SpringApplication.run(Application.class,args);

		try {
			context = new ClassPathXmlApplicationContext(new String[] { "spring/spring-context.xml" });
			context.start();
			threadPool = (ThreadPoolTaskExecutor) context.getBean("threadPool");
			notifyFacade = (NotifyFacade) context.getBean("notifyFacade");
			notifyQueue = (NotifyQueue) context.getBean("notifyQueue");
			startInitFromDB();
			startThread();
			LOG.info("== context start");
		} catch (Exception e) {
			LOG.error("== application start error:", e);
			return;
		}
		synchronized (Application.class) {
			while (true) {
				try {
					Application.class.wait();
				} catch (InterruptedException e) {
					LOG.error("== synchronized error:", e);
				}
			}
		}

	}


	private static void startThread() {
		// Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable() {
		// public void run() {
		// System.out.println(threadPool.getActiveCount() + "-->current active--->" + tasks.size() + "max is--->" + threadPool.getMaxPoolSize());
		// }
		// }, 0, 5, TimeUnit.SECONDS);

		threadPool.execute(new Runnable() {
			public void run() {
				try {
					while (true) {
						// 如果当前活动线程等于最大线程，那么不执行
						if (threadPool.getActiveCount() < threadPool.getMaxPoolSize()) {
							final NotifyTask task = tasks.poll();
							if (task != null) {
								threadPool.execute(new Runnable() {
									public void run() {
										System.out.println(threadPool.getActiveCount() + "---------");
										tasks.remove(task);
										task.run();
									}
								});
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * 从数据库中取一次数据用来当系统启动时初始化
	 */
	@SuppressWarnings("unchecked")
	private static void startInitFromDB() {
		LOG.info("get data from database");

		int pageNum = 1;
		int numPerPage = 500;
		PageParam pageParam = new PageParam(pageNum, numPerPage);

		// 查询状态和通知次数符合以下条件的数据进行通知
		String[] status = new String[] { "101", "102", "200", "201" };
		Integer[] notifyTime = new Integer[] { 0, 1, 2, 3, 4 };
		// 组装查询条件
		Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("statusList", status);
		paramMap.put("notifyTimeList", notifyTime);

		PageBean pageBean = notifyFacade.queryNotifyRecordListPage(pageParam, paramMap);
		while (pageNum <= pageBean.getEndPageIndex()) {
			List<Object> list = pageBean.getRecordList();
			for (int i = 0; i < list.size(); i++) {
				NotifyRecord notifyRecord = (NotifyRecord) list.get(i);
				notifyRecord.setLastNotifyTime(new Date());
				notifyQueue.addElementToList(notifyRecord);
			}
			pageNum++;
			LOG.info(String.format("调用通知服务.notifyFacade.notiFyReCordListPage(%s, %s, %s)", pageNum, numPerPage, paramMap));
			pageBean = notifyFacade.queryNotifyRecordListPage(pageParam, paramMap);
		}
	}













}
