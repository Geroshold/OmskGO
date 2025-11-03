package com.example.go

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

class CatalogFragment : Fragment() {

    // Переменная для RecyclerView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlacesAdapter
    private val placesList = mutableListOf<Place>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Создаем view из layout файла
        val view = inflater.inflate(R.layout.fragment_catalog, container, false)

        // Находим RecyclerView в layout
        recyclerView = view.findViewById(R.id.recyclerView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Настраиваем RecyclerView
        setupRecyclerView()

        // Загружаем данные из JSON
        loadPlacesFromJson()
    }

    private fun setupRecyclerView() {
        // Создаем адаптер
        adapter = PlacesAdapter(placesList)

        // Настраиваем RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadPlacesFromJson() {
        try {
            // Открываем и читаем JSON файл из assets
            val inputStream = requireContext().assets.open("places.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            // Очищаем список перед загрузкой новых данных
            placesList.clear()

            // Парсим каждый объект из JSON массива
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val place = Place(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    description = jsonObject.getString("description"),
                    location = jsonObject.getString("location"),
                    visitors = jsonObject.getInt("visitors"),
                    imageUrl = jsonObject.getString("imageUrl")
                )
                placesList.add(place)
            }

            // Сообщаем адаптеру, что данные изменились
            adapter.notifyDataSetChanged()

        } catch (e: Exception) {
            // Если произошла ошибка, выводим в лог
            e.printStackTrace()
        }
    }
}