package com.devminds.rentify.service;

import com.devminds.rentify.dto.CreateItemDto;
import com.devminds.rentify.dto.ItemDto;
import com.devminds.rentify.entity.*;
import com.devminds.rentify.exception.ItemNotFoundException;
import com.devminds.rentify.repository.AddressRepository;
import com.devminds.rentify.repository.CategoryRepository;
import com.devminds.rentify.repository.ItemRepository;
import com.devminds.rentify.repository.PictureRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ItemService {
    private static final String ITEM_NOT_FOUND_MESSAGE = "Item with %d id not found.";
    private final ItemRepository itemRepository;
    private final ModelMapper modelMapper;
    private final AddressRepository addressRepository;
    private final StorageService storageService;
    private final PictureRepository pictureRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ItemService(ItemRepository itemRepository, ModelMapper modelMapper, AddressRepository addressRepository, StorageService storageService, PictureRepository pictureRepository, CategoryRepository categoryRepository) {
        this.itemRepository = itemRepository;
        this.modelMapper = modelMapper;
        this.addressRepository = addressRepository;
        this.storageService = storageService;
        this.pictureRepository = pictureRepository;
        this.categoryRepository = categoryRepository;
    }

    public Item saveItem(CreateItemDto createItemDto) throws IOException {
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Address addressToAdd = new Address();
        addressToAdd.setCity(createItemDto.getCity());
        addressToAdd.setPostCode(createItemDto.getPostCode());
        addressToAdd.setStreet(createItemDto.getStreet());
        addressToAdd.setStreetNumber(createItemDto.getStreetNumber());
        this.addressRepository.save(addressToAdd);

        Category categoryToSave = this.categoryRepository.findByName(createItemDto.getCategory()).orElse(null);

        List<MultipartFile> pictureFiles = createItemDto.getPictures();
        List<URL> pictureUrls = storageService.uploadFiles(pictureFiles);

        Item itemToSave = this.modelMapper.map(createItemDto, Item.class);
        itemToSave.setAddress(addressToAdd);
        itemToSave.setUser(principal);
        itemToSave.setPostedDate(LocalDateTime.now());
        itemToSave.setCategory(categoryToSave);

        itemToSave.setPictures(new ArrayList<>());
        Item savedItem = this.itemRepository.save(itemToSave);
        List<Picture> picturesToAdd = new ArrayList<>();

        for (int i = 0; i < pictureUrls.size(); i++) {
            Picture picture = new Picture();
            picture.setUrl(pictureUrls.get(i).toString());
            picture.setItem(savedItem);
           picturesToAdd.add(this.pictureRepository.save(picture));
        }
        savedItem.setPictures(picturesToAdd);

        return this.itemRepository.getReferenceById(savedItem.getId());
    }


    public List<ItemDto> getAllItems() {
        return itemRepository.findAll()
                .stream()
                .map(this::mapItemToItemDto)
                .toList();
    }

    public ItemDto getItemById(Long id) {
        return itemRepository.findById(id)
                .map(this::mapItemToItemDto)
                .orElseThrow(() -> new ItemNotFoundException(String.format(ITEM_NOT_FOUND_MESSAGE, id)));
    }

    public List<ItemDto> getItemsByCategoryId(Long id) {
        return itemRepository.findByCategoryId(id)
                .stream()
                .map(this::mapItemToItemDto)
                .toList();
    }

    private ItemDto mapItemToItemDto(Item item) {
        return modelMapper.map(item, ItemDto.class);
    }

    private Item mapItemDtoToItem(ItemDto itemDto) {
        return modelMapper.map(itemDto, Item.class);
    }
}
