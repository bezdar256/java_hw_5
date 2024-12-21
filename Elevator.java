import java.util.Comparator;
import java.util.TreeSet;

/**
 описывает логику работы одного лифт
 */
public class Elevator implements Runnable {

    // состояния лифта 
    public enum ElevSt {
        WAITING,       // ожидание
        MOVING_UP,  // движение вверх
        MOVING_DOWN // движение вниз
    }


    private final int[] requests;     //  число людей, ожидающих на каждом этаже
    private final boolean[] running;  // общий массив-флаг

    public int currFloor = 1; // текущий этаж лифта
    public ElevSt state = ElevSt.WAITING; // состояние лифта 

    public int capacity;         // грузоподъёмность
    public int currLoad = 0;  // сколько людей внутри

    // наборы этажей, куда нужно ехать вверх/вниз
    public TreeSet<Integer> upReq   = new TreeSet<>();
    public TreeSet<Integer> downReq = new TreeSet<>(Comparator.reverseOrder());

    // номер лифта
    private final int elevId;

    /**
    // конструктор лифта
    @param elevId // номер лифта (для логов)
    @param capacity // грузоподъёмность
    @param requests // ссылка на общий массив заявок
    @param running // ссылка на флаг работы
     */
    public Elevator(int elevId, int capacity, int[] requests, boolean[] running) {
        this.elevId = elevId;
        this.capacity   = capacity;
        this.requests   = requests;
        this.running    = running;
    }

    @Override
    public void run() {
        while (running[0]) {
            step();
            try {
                Thread.sleep(100); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[Лифт " + elevId + "] Остановлен.");
    }

    /*
      добавление запроса на этаж floor
      если лифт WAITING — выбираем направление
      если уже движется, добавляем этаж в соответствующий набор
     */
    public synchronized void addReq(int floor) {
        System.out.println("[Лифт " + elevId + "] Получен запрос на этаж " + floor);

        if (state == ElevSt.WAITING) {
            if (floor > currFloor) {
                state = ElevSt.MOVING_UP;
                upReq.add(floor);
            } else if (floor < currFloor) {
                state = ElevSt.MOVING_DOWN;
                downReq.add(floor);
            } else {
                pickupP(floor);
            }
            return;
        }

        if (state == ElevSt.MOVING_UP) {
            if (floor >= currFloor) {
                upReq.add(floor);
            } else {
                downReq.add(floor);
            }
        } else { 
            if (floor <= currFloor) {
                downReq.add(floor);
            } else {
                upReq.add(floor);
            }
        }
    }

    /*
      Оценка "стоимости" для назначения лифту этажа floor
      (чем больше стоимость, тем менее выгодно лифту брать этот запрос)
     */
    public synchronized int addCostReq(int floor) {
        // Если лифт уже полон
        if (currLoad >= capacity) {
            return Integer.MAX_VALUE;
        }

        ElevSt oldState = state;
        int oldFloor = currFloor;
        int oldLoad  = currLoad;

        TreeSet<Integer> oldUp   = new TreeSet<>(upReq);
        TreeSet<Integer> oldDown = new TreeSet<>(downReq);

        addReqTemp(floor);
        int newCost = estRouteCost();

        state = oldState;
        currFloor = oldFloor;
        currLoad = oldLoad;
        upReq = oldUp;
        downReq = oldDown;

        double directionFactor = 1.0;
        if (floor > oldFloor) {
            if (oldState == ElevSt.MOVING_UP || oldState == ElevSt.WAITING) {
                directionFactor -= 0.1;
            } else {
                directionFactor += 0.2;
            }
        } else if (floor < oldFloor) {
            if (oldState == ElevSt.MOVING_DOWN || oldState == ElevSt.WAITING) {
                directionFactor -= 0.1;
            } else {
                directionFactor += 0.2;
            }
        } else {
            directionFactor -= 0.3; 
        }

        // чем больше людей уже внутри, тем "дороже"
        double loadFactor = 1.0 + ((double)oldLoad / (double)capacity)*0.5;

        return (int)(newCost * directionFactor * loadFactor);
    }

    /*
     добавляем запрос, чтобы оценить стоимость (cost)
     */
    private void addReqTemp(int floor) {
        if (state == ElevSt.WAITING) {
            if (floor > currFloor) {
                state = ElevSt.MOVING_UP;
                upReq.add(floor);
            } else if (floor < currFloor) {
                state = ElevSt.MOVING_DOWN;
                downReq.add(floor);
            }
            return;
        }

        if (state == ElevSt.MOVING_UP) {
            if (floor >= currFloor) {
                upReq.add(floor);
            } else {
                downReq.add(floor);
            }
        } else {
            if (floor <= currFloor) {
                downReq.add(floor);
            } else {
                upReq.add(floor);
            }
        }
    }


     //Оценка маршрута по шагам

    public synchronized int estRouteCost() {
        int cost = 0;
        int pos  = currFloor;
        // если лифт стоит или движется вверх, обслужим сначала upReq, потом downReq
        if (state == ElevSt.WAITING || state == ElevSt.MOVING_UP) {
            for (int f : upReq) {
                cost += Math.abs(f - pos);
                pos = f;
            }
            for (int f : downReq) {
                cost += Math.abs(f - pos);
                pos = f;
            }
        } else {
            // иначе лифт движется вниз
            for (int f : downReq) {
                cost += Math.abs(f - pos);
                pos = f;
            }
            cost += Math.abs(pos - 1);
            pos = 1;
            for (int f : upReq) {
                cost += Math.abs(f - pos);
                pos = f;
            }
        }
        cost += Math.abs(pos - 1);
        return cost;
    }

    void step() {
        synchronized (this) {
            // если нет запросов
            if (upReq.isEmpty() && downReq.isEmpty()) {
                if (currFloor != 1) {
                    moveToFloor1();
                } else {
                    state = ElevSt.WAITING;
                }
                return;
            }

            // если лифт должен ехать наверх
            if ((state == ElevSt.WAITING && !upReq.isEmpty()) 
                 || state == ElevSt.MOVING_UP) {

                state = ElevSt.MOVING_UP;

                if (!upReq.isEmpty()) {
                    Integer target = upReq.first();

                    // проверка, остались ли люди на этом этаже
                    if (requests[target - 2] == 0) {
                        upReq.remove(target);
                        System.out.println("[Лифт " + elevId + "] Этаж " + target
                                + " пуст — пропускаем");

                        if (upReq.isEmpty() && downReq.isEmpty()) {
                            moveToFloor1();
                            return;
                        }
                    } else {
                        // едет к target
                        moveTow(target);
                        System.out.println("[Лифт " + elevId + "] Движение к этажу " 
                                + target + " (текущий " + currFloor + ")");

                        if (currFloor == target) {
                            upReq.remove(target);
                            pickupP(target);
                            System.out.println("[Лифт " + elevId + "] Приехали на этаж " + target
                                    + ", загрузка=" + currLoad);

                            if (upReq.isEmpty() && !downReq.isEmpty()) {
                                state = ElevSt.MOVING_DOWN;
                            } else if (upReq.isEmpty() && downReq.isEmpty()) {
                                moveToFloor1();
                            }
                        }
                    }
                } else {
                    if (!downReq.isEmpty()) {
                        state = ElevSt.MOVING_DOWN;
                    } else {
                        moveToFloor1();
                    }
                }
            } 
            // если лифт должен ехать вниз
            else if ((state == ElevSt.WAITING && !downReq.isEmpty()) 
                      || state == ElevSt.MOVING_DOWN) {

                state = ElevSt.MOVING_DOWN;

                if (!downReq.isEmpty()) {
                    Integer target = downReq.first();
                    if (requests[target - 2] == 0) {
                        downReq.remove(target);
                        System.out.println("[Лифт " + elevId + "] Этаж " + target
                                + " уже пуст — пропускаем!");

                        if (upReq.isEmpty() && downReq.isEmpty()) {
                            moveToFloor1();
                            return;
                        }
                    } else {
                        // едет вниз
                        moveTow(target);
                        System.out.println("[Лифт " + elevId + "] Движение к этажу " 
                                + target + " (текущий " + currFloor + ")");

                        if (currFloor == target) {
                            downReq.remove(target);
                            pickupP(target);
                            System.out.println("[Лифт " + elevId + "] Приехали на этаж " + target
                                    + ", загрузка=" + currLoad);

                            if (downReq.isEmpty() && !upReq.isEmpty()) {
                                moveToFloor1();
                            } else if (downReq.isEmpty() && upReq.isEmpty()) {
                                moveToFloor1();
                            }
                        }
                    }
                } else {
                    if (!upReq.isEmpty()) {
                        moveToFloor1();
                    } else {
                        moveToFloor1();
                    }
                }
            }

            // если двигаемся вниз и текущий этаж > 1, пробуем подобрать людей "по пути"
            if (state == ElevSt.MOVING_DOWN && currFloor > 1) {
                pickupP(currFloor);
            }
        }
    }

    // лифт возвращается на первый этаж, если нет заявок
     
    void moveToFloor1() {
        if (currFloor > 1) {
            state = ElevSt.MOVING_DOWN;
            currFloor--;
            System.out.println("[Лифт " + elevId + "] возвращается к этажу " + currFloor);
            if (currFloor == 1) {
                System.out.println("[Лифт " + elevId + "] 1 этаж: выгрузка");
                currLoad = 0;
                state = ElevSt.WAITING;
            }
        } else if (currFloor < 1) {
            state = ElevSt.MOVING_UP;
            currFloor++;
            System.out.println("[Лифт " + elevId + "] возвращается к этажу " + currFloor);
            if (currFloor == 1) {
                System.out.println("[Лифт " + elevId + "] 1 этаж: выгрузка");
                currLoad = 0;
                state = ElevSt.WAITING;
            }
        } else {
            System.out.println("[Лифт " + elevId + "] уже 1 этаж, выгрузка");
            currLoad = 0;
            state = ElevSt.WAITING;
        }
    }


    void moveTow(int floor) {
        if (floor > currFloor) {
            currFloor++;
        } else if (floor < currFloor) {
            currFloor--;
        }
    }


    void pickupP(int floor) {
        if (floor == 1) {
            System.out.println("[Лифт " + elevId + "] лифт пуст, все вышли");
            currLoad = 0;
            return;
        }
        int idx = floor - 2;
        if (idx >= 0 && idx < requests.length) {
            int waiting = requests[idx];
            if (waiting > 0 && currLoad < capacity) {
                int canTake = Math.min(capacity - currLoad, waiting);
                requests[idx] -= canTake;
                currLoad += canTake;
                System.out.println("[Лифт " + elevId + "] подобрал " 
                        + canTake + " чел. на этаже " + floor 
                        + "; загрузка=" + currLoad);
            }
        }
    }
}
