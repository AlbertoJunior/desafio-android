package com.example.desafio_android_alberto_junior.view.comic;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.desafio_android_alberto_junior.MainApplication;
import com.example.desafio_android_alberto_junior.R;
import com.example.desafio_android_alberto_junior.animation.EndAnimation;
import com.example.desafio_android_alberto_junior.model.Comic;
import com.example.desafio_android_alberto_junior.model.Image;
import com.example.desafio_android_alberto_junior.databinding.FragmentDetailsComicBinding;
import com.example.desafio_android_alberto_junior.utils.ImageFromURL;
import com.example.desafio_android_alberto_junior.vm.ComicViewModel;
import com.example.desafio_android_alberto_junior.web.WebClient;
import com.example.desafio_android_alberto_junior.web.comic.ComicDataWrapper;
import com.google.gson.Gson;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.SocketTimeoutException;

import javax.inject.Inject;

import static com.example.desafio_android_alberto_junior.utils.ImageFromURL.IMAGE_PORTRAIT_FANTASTIC;

public class ComicDetails extends Fragment {
    @Inject
    WebClient webClient;

    @Inject
    ComicViewModel viewModel;

    private FragmentDetailsComicBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_details_comic, container, false);
        MainApplication.getComponent().inject(this);

        binding.btnVoltar.setOnClickListener(v -> navigationBack());
        binding.btnAtualizar.setOnClickListener(v -> getComicsInWeb(viewModel.getCharacterId(), 100, 0));
        return binding.getRoot();
    }

    private void navigationBack() {
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            final Animation animation = AnimationUtils.loadAnimation(requireContext(), R.anim.fast_fade_out);
            animation.setAnimationListener(new EndAnimation(fragmentManager::popBackStack));
            binding.getRoot().setVisibility(View.GONE);
            binding.getRoot().startAnimation(animation);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        binding.includeToolbar.toolbar.setTitle("HQ mais cara");

        if (getArguments() != null) {
            int characterId = getArguments().getInt("character_id");
            viewModel.setCharacterId(characterId);
            setupInterface();
            getComicsInWeb(characterId, 100, 0);
        }
    }

    private void getComicsInWeb(int characterId, int limit, int offset) {
        viewModel.setShowLoading(true);
        viewModel.setShowButtonRefresh(false);

        webClient.getCharacterComicsEnqueue(characterId, limit, offset, new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                defaultErrorTreatment();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String body = response.body().string();
                        ComicDataWrapper dataWrapper = new Gson().fromJson(body, ComicDataWrapper.class);

                        // adicionando as revistas
                        viewModel.getComicList().addAll(dataWrapper.getData().getResults());

                        // se for menor que o limite então buscou todas
                        if (dataWrapper.getData().getTotal() == limit)
                            getComicsInWeb(characterId, limit, offset + 1);
                        else {
                            viewModel.setListFinish(true);
                            viewModel.setShowButtonRefresh(false);
                            searchImageHandler();
                        }
                    } catch (SocketTimeoutException e){
                        defaultErrorTreatment();
                    }
                }
            }
        });
    }

    private void defaultErrorTreatment() {
        viewModel.setMessageError("O Servidor da S.H.I.E.L.D. não respondeu a tempo");
        viewModel.setShowError(true);
        viewModel.setShowButtonRefresh(true);
        viewModel.setShowLoading(false);
    }

    private void setComicInfo(Comic current) {
        if (current != null) {
            viewModel.setCurrent(current);
            viewModel.setShowLoading(false);
            viewModel.setShowError(false);
            viewModel.setShowCurrent(true);
            searchImageHandler();
        }
    }

    private void searchImageHandler() {
        new Handler(Looper.getMainLooper()).post(this::searchImage);
    }

    private void searchImage() {
        final Comic comic = viewModel.getCurrent().get();
        if (comic != null) {
            final Image thumbnail = comic.getThumbnail();
            final String originalPath = thumbnail.getPath();

            //se já possuir uma imagem seta e retorna
            if (thumbnail.getDrawableImage() != null) {
                viewModel.setImage(thumbnail.getDrawableImage());
                binding.cardImage.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in));
                return;
            }

            // se for uma path inválida seta um default e retorna
            if (originalPath == null || originalPath.isEmpty() || originalPath.contains("image_not_available")) {
                binding.ciImagem.setImageResource(ImageFromURL.DEFAULT_XLARGE_NOT_AVAILABLE);
                binding.cardImage.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in));
                return;
            }

            // buscando a imagem na web
            String path = String.format("%s/%s.%s", originalPath, IMAGE_PORTRAIT_FANTASTIC, thumbnail.getExtension());
            Glide.with(requireContext())
                    .load(path)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            binding.pbBuscar.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out));
                            binding.tvCarregando.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out));
                            binding.cardImage.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            binding.pbBuscar.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out));
                            binding.tvCarregando.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out));
                            binding.cardImage.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in));
                            return false;
                        }
                    })
                    .placeholder(ImageFromURL.DEFAULT_XLARGE_NOT_AVAILABLE)
                    .into(binding.ciImagem);
        }
    }

    private void setupInterface() {
        binding.setComic(viewModel);
        viewModel.setShowCurrent(false);
        viewModel.setShowError(false);

        final Animation animation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in);
        binding.getRoot().startAnimation(animation);
    }
}
