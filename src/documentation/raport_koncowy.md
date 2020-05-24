### Agent lokalny
Celem agenta lokalnego jest obserwowanie zużycia zasobów przez klienta i na ich podstawie przewidywanie ich zużycia w chwili *t+1*. Aby zasymulować sytuację na której możliwe będzie przetestowanie systemu agent lokalny generuje wartości funkcji *sin* od *t* i próbuje przewidzieć wartość tej funkcji w chwili *t+1*. Działanie agenta jest niezależne od funkcji symulującej zużycie zasobów systemowych, więc jest on w stanie przewidzieć wartość dowolnej funkcji.

#### Kroki podejmowane przez agenta lokalnego w celu przewidzenia zużycia zasobów:
1. Wczytanie historii zużycia zasobów z poprzednio wykonywanej pracy z pliku.
2. Przekształcenie historii do postaci odpowiedniej dla modelu.
3. Trenowanie modelu.
4. Generowanie kolejnych wartości funkcji *sin* zależności od czasu i predykcja wartości w chwili t+1
5. Zapisanie historii. 

#### Dokładny opis działania agenta lokalnego
**1. Wczytanie historii zużycia zasobów z poprzednio wykonywanej pracy z pliku**

Historia zużycia zasobów jest przechowywana w pliku txt, w którego jednej linii przechowywana jest informacja na temat ilości zużytych danych w postaci licby zmiennoprzecinkowej oraz chwili czasu ,w której wystąpiło dane zdarzenie. Fragment pliku jest widoczny na zdjęciu poniżej.

<p align="center">
  <img src = "./raport_koncowy_zdjecia/history_snapshot.png"/>
   <figcaption>Fragment pliku z historią zużycia zasobów</figcaption>
</p>

**2. Przekształcenie historii do postaci odpowiedniej dla modelu**

Do predykcji wartości w chwili *t+1*, model wykorzystuje wartości dla chwil *t, t-1...t-n+1*, które są jego atrybutami. W przypadku naszego systemu wartość n wynosi 10. Taka wartość pozwala zachować odpowiedni balans pomiędzy czasem uczenia modelu a jego precyzją. Data Frame jaki powstaje z listy o długości m ma kształt *m x n+1* (n kolumn to atrybuty, a 1 to etykieta, czyli wartość jaką model powinien na ich podstawie przewidzieć). Poniższe zdjęcie przedstawia fragment zbioru uczącego:
<p align="center">
  <img src = "./raport_koncowy_zdjecia/training_dataset_snapshot.png"/>
   <figcaption>Fragment zbioru uczącego</figcaption>
</p>

Na zdjęciu możemy zauważyć, że wartość kolumny x wiersza y jest zawsze taka sama jak wartość kolumny x+1 i wiersza y-1.

**3. Trenowanie modelu**
Chcąc osiągnąć jak największą precyzję w przewidywaniu zużycia zasobów systemowych przetestowane zostały różne modele statystyczne, z których najlepszym okazał się *Gradient Boosted Tree Regression Model*, osiągając średni błąd kwadratowy na zbiorze testowym o wartości jedynie 1.8%. Dla porównania, model regresji liniowej proponowany w referencyjnych rozwiązaniach 
osiągał błąd na poziomie 24.95%. Tak więc w powyższym przypadku zastosowanie bardziej złożonego modelu pozwoliło osiągnąć ponad 13-krotnie lepszy efekt! 

Na poniższych zdjęciach możemy zobaczyć jak wyżej opisane modele przewidują wartość funkcji sin:
<p align="center">
  <img src = "./raport_koncowy_zdjecia/real_predicted_rgb.png"/>
</p>

<p align="center">
  <img src = "./raport_koncowy_zdjecia/real_predicted_lnr.png"/>
   <figcaption>Predicted vs Real Values for Linear Regression, RMSE = 24.95</figcaption>
</p>

**4. Generowanie kolejnych wartości funckji sin w zależności od czasu i predykcja wartości w chwili t+1.**

Aby zasymulować działania użytkownika w systemie agent lokalny generuje wartości sin w zależności od czasu w pętli wykonującej się x razy. Co każdą iteracją model przewiduje wartość funkcji w chwili t+1 i wysyła ją do agenta globalnego w celu uzyskania lub oddania części przydzielanych mu zasobów. Aby model predykcyjny był aktualny, jest on trenowany od nowa co ustaloną ilość iteracji k. Podczas kolejnych treningów pod uwagę brane są tylko wartości z zakresu *(t - k : t )*. Ma to na celu dostosowanie modelu do aktualnego zużycia zasobów w systemie i uodpornienie modelu na funkcje nieokresowe. Tak zaprojektowany system jest w stanie przewidywać wartości dowolnej funckji, której charakter jest zależny od jej poprzednich wartości.  

**5. Zapisanie historii.**

Po zakończeniu generowania wartości, są one zapisane do pliku, aby można było z nich skorzystać podczas trenowania modelu przy kolejnym uruchomieniu agenta.
