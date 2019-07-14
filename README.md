# CustomEventBus
自定义生撸事件总线EventBus，使用起来比较方便，指定在UI线程处理总线事件或者在工作线程池时处理总线事件均可。下面是对几个类的简介：
事件总线服务，专门用于处理多进程事件分发，为说明事件分发流程，定义角色为:主进程事件总线 MainUI--EventBus，服务事件总线 Service--EventBus，
EventBusService 运行于主进程的服务
(1)主进程发事件：
Caller -->postEvent  ---->通过MainUI--EventBus 分发到当前进程的事件观察者 --> 触发事件观察者的 handleBusEvent调用
---->调用EventBusService的handleBusEvent -->通过注册进来的服务进程的Messenger发送到Service-EventBus
(接续)的ClientHandler.handleMessage -->postEvent --> 通过Service-EventBus分发到服务进程的事件察者
-->(3)服务进程收到事件。
(2)服务进程发事件：
Caller -->postEvent  ---->通过Service-EventBus 分发到当前进程的事件观察者 --> 解发事件观察者的 handleBusEvent调用
---->通过ServiceMessenger发到EventBusService的MyHandler.handleMessage -->postEvent
-->(4)EventBusService收事件
(3)服务进程收事件
ClientHandler-->handleMessage -->通过Service-EventBus分发到当前进程的事件观察者（要防止再发回到主进程）
(4)EventBusService收事件
  EventBusService.MyHandler-->handleMessage -->通过MainUI--EventBus 分发到当前进程的事件观察者（要防止被再发回到服务进程）
