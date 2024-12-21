import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class ElevatorRequestSimulation extends JFrame {

    // параметры симуляции
    public static final int floorCount = 10;          // количество этажей
    public static final int elevatorCount = 3;        // количество лифтов
    public static final double requestProbability = 0.3; // вероятность появления заявки на каждом шаге
    public static final int simulationSteps = 200;    // сколько шагов симуляции выполняем
    public static final int peoplePerRequestMin = 1;  // минимум людей в заявке
    public static final int peoplePerRequestMax = 5;  // максимум людей в заявке
    public static final int REFRESH_DELAY = 200;      // задержка перерисовки (мс)

    // общий массив, сколько людей на каждом этаже
    public static int[] requests = new int[floorCount - 1];

    // массив-флаг для остановки/запуска симуляции
    public static boolean[] running = new boolean[]{ false };

    // лифты
    Elevator[] elevators = new Elevator[elevatorCount];

    // компоненты GUI
    JButton startButton;
    JButton stopButton;
    BuildingPanel buildingPanel;

    // потоки
    Thread simulationThread;
    List<Thread> elevatorThreads = new ArrayList<>();

    Random rand = new Random();

    public ElevatorRequestSimulation() {
        super("Симуляция работы лифтов");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 800);
        setLayout(new BorderLayout());

        // создание лифтов
        elevators[0] = new Elevator(1, 5,  requests, running);
        elevators[1] = new Elevator(2, 5, requests, running);
        elevators[2] = new Elevator(3, 10, requests, running);

        // панель управления
        JPanel controlPanel = new JPanel();
        startButton = new JButton("Запустить симуляцию");
        stopButton  = new JButton("Остановить симуляцию");
        stopButton.setEnabled(false);

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        add(controlPanel, BorderLayout.SOUTH);

        // панель отрисовки здания
        buildingPanel = new BuildingPanel();
        add(buildingPanel, BorderLayout.CENTER);

        // обработчики кнопок
        startButton.addActionListener(e -> {
            if (!running[0]) {
                running[0] = true;
                startButton.setEnabled(false);
                stopButton.setEnabled(true);

                // обнуление заявков
                for (int i = 0; i < requests.length; i++) {
                    requests[i] = 0;
                }

                // запуск потоков лифтов
                elevatorThreads.clear();
                for (Elevator elev : elevators) {
                    Thread t = new Thread(elev);
                    t.start();
                    elevatorThreads.add(t);
                }

                // запуск потоков симуляции
                simulationThread = new Thread(this::simulate);
                simulationThread.start();

                System.out.println("[СИМУЛЯЦИЯ] Симуляция запущена");
            }
        });

        stopButton.addActionListener(e -> {
            if (running[0]) {
                running[0] = false;
                stopButton.setEnabled(false);
                System.out.println("[СИМУЛЯЦИЯ] Остановка симуляции");
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    /*
    цикл генерации заявок и логика заявок для лифтов
     */
    void simulate() {
        for (int step = 1; step <= simulationSteps && running[0]; step++) {
            // генерируем новую заявку
            if (rand.nextDouble() < requestProbability) {
                int requestFloor = rand.nextInt(floorCount - 1) + 2; 
                int peopleCount  = peoplePerRequestMin 
                        + rand.nextInt(peoplePerRequestMax - peoplePerRequestMin + 1);

                synchronized (ElevatorRequestSimulation.class) {
                    requests[requestFloor - 2] += peopleCount;
                }

                System.out.println("[СИМ] " + peopleCount 
                        + " чел. вызвали лифт на этаж " + requestFloor);
                dispatchRequests();
            }

            SwingUtilities.invokeLater(buildingPanel::repaint);

            try {
                Thread.sleep(REFRESH_DELAY);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        running[0] = false;
        System.out.println("[СИМУЛЯЦИЯ] Симуляция завершена");
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, 
                "Симуляция завершена", 
                "инфо", 
                JOptionPane.INFORMATION_MESSAGE
            );
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    /*
    логика назначения заявок - пробегаем по этажам (сверху вниз) и ищем, где есть люди
    находим лифт с наименьшей стоимостью (additionalCost), назначаем ему этот этаж
    */
    void dispatchRequests() {
        synchronized (ElevatorRequestSimulation.class) {
            for (int floor = floorCount; floor >= 2; floor--) {
                int idx = floor - 2;
                if (requests[idx] <= 0) continue;
                int waiting = requests[idx];
                if (waiting > 0) {
                    Elevator bestElevator = null;
                    int bestCost = Integer.MAX_VALUE;
                    // ищем лифт с минимальной cost
                    for (Elevator e : elevators) {
                        synchronized (e) {
                            int cost = e.addCostReq(floor);
                            if (cost < bestCost) {
                                bestCost = cost;
                                bestElevator = e;
                            }
                        }
                    }
                    if (bestElevator != null && bestCost != Integer.MAX_VALUE) {
                        // назначаем
                        synchronized (bestElevator) {
                            bestElevator.addReq(floor);
                        }
                    }
                }
            }
        }
    }

    /*
    отрисовка схемы здания и лифтов
    */
    class BuildingPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int panelWidth  = getWidth();
            int panelHeight = getHeight();
            int floors      = floorCount;
            int margin      = 50;
            int floorHeight = (panelHeight - 2 * margin) / floors;

            synchronized (ElevatorRequestSimulation.class) {
                // этажи и людей
                for (int f = 1; f <= floors; f++) {
                    int y = margin + (floors - f) * floorHeight;
                    boolean hasPeople = false;
                    if (f > 1) {
                        int idx = f - 2;
                        if (idx >= 0 && idx < requests.length && requests[idx] > 0) {
                            hasPeople = true;
                        }
                    }
                    if (hasPeople) {
                        g.setColor(new Color(255, 200, 200));
                        g.fillRect(margin, y, panelWidth - 2*margin, floorHeight);
                    }

                    g.setColor(Color.LIGHT_GRAY);
                    g.drawLine(margin, y, panelWidth - margin, y);

                    String floorInfo;
                    if (f == 1) {
                        floorInfo = "Этаж 1 (Лобби)";
                    } else {
                        int idx = f - 2;
                        int count = (idx >= 0 && idx < requests.length)
                                ? requests[idx] : 0;
                        floorInfo = String.format("Этаж %d: %d ждут", f, count);
                    }
                    g.setColor(Color.BLACK);
                    g.drawString(floorInfo, margin + 10, y + floorHeight/2);
                }
            }

            // лифты
            int elevatorWidth = 20;
            for (int i = 0; i < elevatorCount; i++) {
                Elevator e = elevators[i];
                int currentFloor;
                int load;
                int cap;
                synchronized (e) {
                    currentFloor = e.currFloor;
                    load         = e.currLoad;
                    cap          = e.capacity;
                }

                int y = margin + (floors - currentFloor) * floorHeight + floorHeight/2;
                int x = panelWidth - margin - (elevatorWidth + 50) * (i + 1);

                // цвет лифта
                Color c = Color.getHSBColor((float) i / elevatorCount, 1f, 1f);
                g.setColor(c);
                g.fillRect(x, y - elevatorWidth/2, elevatorWidth, elevatorWidth);

                // подпись: "Л1(3/5)"
                g.setColor(Color.BLACK);
                g.drawString("Л" + (i+1) + "(" + load + "/" + cap + ")",
                        x, y - elevatorWidth/2 - 2);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ElevatorRequestSimulation::new);
    }
}
