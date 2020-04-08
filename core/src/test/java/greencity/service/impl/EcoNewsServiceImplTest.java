package greencity.service.impl;

import greencity.ModelUtils;
import greencity.TestConst;
import greencity.constant.AppConstant;
import greencity.constant.RabbitConstants;
import greencity.dto.PageableDto;
import greencity.dto.econews.AddEcoNewsDtoRequest;
import greencity.dto.econews.AddEcoNewsDtoResponse;
import greencity.dto.econews.EcoNewsDto;
import greencity.entity.EcoNews;
import greencity.entity.localization.EcoNewsTranslation;
import greencity.exception.exceptions.NotFoundException;
import greencity.exception.exceptions.NotSavedException;
import greencity.message.AddEcoNewsMessage;
import greencity.repository.EcoNewsRepo;
import greencity.repository.EcoNewsTranslationRepo;
import greencity.service.*;
import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(SpringExtension.class)
public class EcoNewsServiceImplTest {
    @Mock
    EcoNewsRepo ecoNewsRepo;

    @Mock
    EcoNewsTranslationRepo ecoNewsTranslationRepo;

    @Mock
    ModelMapper modelMapper;

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    NewsSubscriberService newsSubscriberService;

    @Mock
    UserService userService;

    @Mock
    TagService tagService;

    @Mock
    LanguageService languageService;

    @Mock
    FileService fileService;

    @InjectMocks
    private EcoNewsServiceImpl ecoNewsService;

    private AddEcoNewsDtoRequest addEcoNewsDtoRequest = ModelUtils.getAddEcoNewsDtoRequest();
    private EcoNews ecoNews = ModelUtils.getEcoNews();
    private AddEcoNewsDtoResponse addEcoNewsDtoResponse = ModelUtils.getAddEcoNewsDtoResponse();

    @Test
    public void save() throws MalformedURLException {
        MultipartFile image = ModelUtils.getFile();
        when(modelMapper.map(addEcoNewsDtoRequest, EcoNews.class)).thenReturn(ecoNews);
        when(modelMapper.map(ecoNews, AddEcoNewsDtoResponse.class)).thenReturn(addEcoNewsDtoResponse);
        when(languageService.extractLanguageCodeFromRequest()).thenReturn(AppConstant.DEFAULT_LANGUAGE_CODE);
        when(ecoNewsTranslationRepo.findByEcoNewsAndLanguageCode(ecoNews, AppConstant.DEFAULT_LANGUAGE_CODE))
            .thenReturn(ModelUtils.getEcoNewsTranslation());
        when(newsSubscriberService.findAll()).thenReturn(Collections.emptyList());
        when(userService.findByEmail(TestConst.EMAIL)).thenReturn(ModelUtils.getUser());
        when(tagService.findByName("tag")).thenReturn(ModelUtils.getTag());
        when(languageService.findByCode(AppConstant.DEFAULT_LANGUAGE_CODE))
            .thenReturn(ModelUtils.getLanguage());
        when(ecoNewsRepo.save(ecoNews)).thenReturn(ecoNews);
        when(fileService.upload(image)).thenReturn(ModelUtils.getUrl());

        assertEquals(addEcoNewsDtoResponse, ecoNewsService.save(addEcoNewsDtoRequest,
            image, TestConst.EMAIL));
        addEcoNewsDtoResponse.setTitle("Title");
        verify(rabbitTemplate).convertAndSend(null, RabbitConstants.ADD_ECO_NEWS_ROUTING_KEY,
            new AddEcoNewsMessage(Collections.emptyList(), addEcoNewsDtoResponse));
        addEcoNewsDtoResponse.setTitle("test title");
    }

    @Test()
    public void saveThrowsNotSavedException() throws MalformedURLException {
        MultipartFile image = ModelUtils.getFile();
        when(modelMapper.map(addEcoNewsDtoRequest, EcoNews.class)).thenReturn(ecoNews);
        when(ecoNewsRepo.save(ecoNews)).thenThrow(DataIntegrityViolationException.class);
        when(userService.findByEmail(TestConst.EMAIL)).thenReturn(ModelUtils.getUser());
        when(fileService.upload(image)).thenReturn(ModelUtils.getUrl());

        assertThrows(NotSavedException.class, () ->
            ecoNewsService.save(addEcoNewsDtoRequest, image, TestConst.EMAIL)
        );
    }

    @Test
    public void getThreeLastEcoNews() {
        ZonedDateTime zonedDateTime = ZonedDateTime.now();

        EcoNewsDto ecoNewsDto =
            new EcoNewsDto(zonedDateTime, "test image path", 1L, "test title", "test text",
                ModelUtils.getEcoNewsAuthorDto(), Collections.emptyList());
        EcoNewsTranslation ecoNewsTranslation =
            new EcoNewsTranslation(1L, null, "test title", "test text", null);

        List<EcoNewsDto> dtoList = Collections.singletonList(ecoNewsDto);

        when(ecoNewsTranslationRepo.getNLastEcoNewsByLanguageCode(3, "en"))
            .thenReturn(Collections.singletonList(ecoNewsTranslation));
        when(modelMapper.map(ecoNewsTranslation, EcoNewsDto.class)).thenReturn(ecoNewsDto);
        assertEquals(dtoList, ecoNewsService.getThreeLastEcoNews("en"));
    }

    @Test
    public void getThreeLastEcoNewsNotFound() {
        List<EcoNewsTranslation> ecoNewsTranslations = new ArrayList<>();
        List<EcoNewsDto> ecoNewsDtoList = new ArrayList<>();

        when(ecoNewsTranslationRepo.getNLastEcoNewsByLanguageCode(anyInt(), anyString()))
            .thenReturn(ecoNewsTranslations);

        assertThrows(NotFoundException.class, () ->
            assertEquals(ecoNewsDtoList, ecoNewsService.getThreeLastEcoNews("en"))
        );
    }

    @Test
    public void findAll() {
        ZonedDateTime now = ZonedDateTime.now();

        EcoNewsTranslation ecoNewsTranslation =
            new EcoNewsTranslation(1L, null, "test title", "test text", null);
        List<EcoNewsTranslation> ecoNewsTranslations = Collections.singletonList(ecoNewsTranslation);

        PageRequest pageRequest = new PageRequest(0, 2);
        Page<EcoNewsTranslation> translationPage = new PageImpl<EcoNewsTranslation>(ecoNewsTranslations,
            pageRequest, ecoNewsTranslations.size());

        List<EcoNewsDto> dtoList = Collections.singletonList(
            new EcoNewsDto(now, "test image path", 1L, "test title", "test text",
                ModelUtils.getEcoNewsAuthorDto(), Collections.emptyList())
        );
        PageableDto<EcoNewsDto> pageableDto = new PageableDto<EcoNewsDto>(dtoList, dtoList.size(), 0);

        when(ecoNewsTranslationRepo.findAllByLanguageCode(pageRequest, "en")).thenReturn(translationPage);
        when(modelMapper.map(ecoNewsTranslation, EcoNewsDto.class))
            .thenReturn(dtoList.get(0));
        assertEquals(pageableDto, ecoNewsService.findAll(pageRequest, "en"));
    }

    @Test
    public void findById() {
        EcoNews ecoNews = ModelUtils.getEcoNews();
        when(ecoNewsRepo.findById(1L)).thenReturn(Optional.of(ecoNews));
        assertEquals(ecoNews, ecoNewsService.findById(1L));
    }

    @Test
    public void delete() {
        doNothing().when(ecoNewsRepo).deleteById(1L);
        when(ecoNewsRepo.findById(anyLong()))
            .thenReturn(Optional.of(ModelUtils.getEcoNews()));
        ecoNewsService.delete(1L);
        verify(ecoNewsRepo, times(1)).deleteById(1L);
    }
}


